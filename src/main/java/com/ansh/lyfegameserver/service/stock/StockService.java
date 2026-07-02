package com.ansh.lyfegameserver.service.stock;

import com.ansh.lyfegameserver.data.LimitOrder;
import com.ansh.lyfegameserver.data.Stock;
import com.ansh.lyfegameserver.data.UserIPO;
import com.ansh.lyfegameserver.data.UserStockHolding;
import com.ansh.lyfegameserver.data.Users;
import com.ansh.lyfegameserver.dto.stock.*;
import com.ansh.lyfegameserver.dto.user.UserResponse;
import com.ansh.lyfegameserver.repository.LimitOrderRepository;
import com.ansh.lyfegameserver.repository.StockRepository;
import com.ansh.lyfegameserver.repository.UserRepository;
import com.ansh.lyfegameserver.websocket.StockPriceBroadcaster;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class StockService {

    /** Cooldown between trades on the same stock, in milliseconds (30 real-seconds). */
    private static final long TRADE_COOLDOWN_MS = 30_000L;

    /** Wash-trade window: selling within this period after buying triggers a penalty. */
    private static final long WASH_TRADE_WINDOW_MS = 60_000L;

    /** Wash-trade penalty fraction applied to gains (20%). */
    private static final double WASH_TRADE_PENALTY = 0.20;

    /**
     * Maximum allowed pool impact per trade as a fraction of the pool's Branks reserve.
     * A trade that would consume more than this percentage of the pool is rejected.
     */
    private static final double MAX_POOL_IMPACT_FRACTION = 0.10;

    /** Minimum Branks balance required to launch an IPO. */
    private static final long IPO_MIN_BRANKS = 1_000_000L;

    /**
     * Maximum bond holding value (in Branks) that earns yield per payout.
     * Yield is paid on min(holdingValue, this cap), so payouts plateau instead of
     * scaling linearly with holdings — preventing the compounding runaway where large
     * holders earned millions of Branks every game-day.
     */
    private static final long BOND_MAX_YIELDABLE_VALUE = 1_000_000L;

    private final StockRepository stockRepository;
    private final LimitOrderRepository limitOrderRepository;
    private final UserRepository userRepository;
    private final StockPriceBroadcaster broadcaster;

    public StockService(StockRepository stockRepository,
                        LimitOrderRepository limitOrderRepository,
                        UserRepository userRepository,
                        StockPriceBroadcaster broadcaster) {
        this.stockRepository = stockRepository;
        this.limitOrderRepository = limitOrderRepository;
        this.userRepository = userRepository;
        this.broadcaster = broadcaster;
    }

    // ─── Queries ────────────────────────────────────────────────────────────

    public List<StockInfo> getAllStocks() {
        return stockRepository.findAll().stream()
            .map(this::toStockInfo)
            .toList();
    }

    public StockInfo getStock(String stockId) {
        Stock stock = requireStock(stockId);
        return toStockInfo(stock);
    }

    public PortfolioResponse getPortfolio(String clerkId) {
        Users user = requireUser(clerkId);

        List<PortfolioResponse.HoldingInfo> holdings = new ArrayList<>();

        for (UserStockHolding h : user.getStockHoldings()) {
            Optional<Stock> stockOpt = stockRepository.findById(h.getStockId());
            if (stockOpt.isEmpty() || h.getSharesOwned() <= 0) continue;
            Stock stock = stockOpt.get();
            double price = stock.getCurrentPrice();
            long currentValue = (long) Math.floor(h.getSharesOwned() * price);
            holdings.add(new PortfolioResponse.HoldingInfo(
                stock.getId(), stock.getTicker(), stock.getName(),
                h.getSharesOwned(), price, currentValue
            ));
        }

        long netWorth = computeNetWorth(user);

        return new PortfolioResponse(0, "Success", holdings, netWorth, user.getBranks());
    }

    /**
     * Total net worth = Branks + holdings valued at market + retained founder equity.
     * Shared by the portfolio endpoint and the annual tax assessment.
     */
    public long computeNetWorth(Users user) {
        long holdingsValue = 0L;
        for (UserStockHolding h : user.getStockHoldings()) {
            Optional<Stock> stockOpt = stockRepository.findById(h.getStockId());
            if (stockOpt.isEmpty() || h.getSharesOwned() <= 0) continue;
            holdingsValue += (long) Math.floor(h.getSharesOwned() * stockOpt.get().getCurrentPrice());
        }

        long founderEquity = 0L;
        if (user.getIpo() != null && user.getIpo().getFounderSharesRetained() > 0) {
            Optional<Stock> ipoStock = stockRepository.findById(user.getIpo().getStockId());
            if (ipoStock.isPresent()) {
                founderEquity = (long) Math.floor(
                    user.getIpo().getFounderSharesRetained() * ipoStock.get().getCurrentPrice()
                );
            }
        }

        return user.getBranks() + holdingsValue + founderEquity;
    }

    // ─── Trading ────────────────────────────────────────────────────────────

    public TradeResult executeBuy(String clerkId, String stockId, long quantity) {
        Users user = requireUser(clerkId);
        Stock stock = requireStock(stockId);

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive.");
        }

        rejectFounderSelfTrade(clerkId, stock);

        UserStockHolding holding = getOrCreateHolding(user, stockId);
        enforceCooldown(holding, stock.getTicker());

        // Validate trade size doesn't exceed MAX_POOL_IMPACT_FRACTION of pool
        long maxBranksImpact = (long) (stock.getLiquidityBranks() * MAX_POOL_IMPACT_FRACTION);
        long cost = computeBuyCost(stock, quantity);
        if (cost > maxBranksImpact) {
            throw new IllegalStateException(
                "Trade too large. Reduce quantity — max single-trade impact is 10% of pool depth."
            );
        }

        if (user.getBranks() < cost) {
            throw new IllegalStateException(
                "Insufficient Branks. Cost: " + cost + ", balance: " + user.getBranks()
            );
        }

        // Also check shares are available in pool
        if (quantity >= stock.getLiquidityShares()) {
            throw new IllegalStateException("Not enough shares in pool.");
        }

        // Execute: deduct Branks, update pool, update holding
        user.setBranks(user.getBranks() - cost);
        stock.setLiquidityBranks(stock.getLiquidityBranks() + cost);
        stock.setLiquidityShares(stock.getLiquidityShares() - quantity);

        holding.setSharesOwned(holding.getSharesOwned() + quantity);
        long now = System.currentTimeMillis();
        holding.setLastTradedAt(now);
        holding.setLastBuyAt(now);

        com.ansh.lyfegameserver.service.activity.ActivityService.record(
            user, "TRADE", -cost, "Bought " + quantity + " " + stock.getTicker());

        stock.appendPrice(stock.getCurrentPrice());
        stockRepository.save(stock);
        Users saved = userRepository.save(user);

        double avgPrice = (double) cost / quantity;
        broadcaster.broadcast(stock);

        return new TradeResult(saved, quantity, avgPrice, cost, stock.getCurrentPrice());
    }

    public TradeResult executeSell(String clerkId, String stockId, long quantity) {
        Users user = requireUser(clerkId);
        Stock stock = requireStock(stockId);

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive.");
        }

        rejectFounderSelfTrade(clerkId, stock);

        UserStockHolding holding = getOrCreateHolding(user, stockId);
        enforceCooldown(holding, stock.getTicker());

        if (holding.getSharesOwned() < quantity) {
            throw new IllegalStateException(
                "Insufficient shares. Owned: " + holding.getSharesOwned()
            );
        }

        // Validate trade size
        long maxBranksImpact = (long) (stock.getLiquidityBranks() * MAX_POOL_IMPACT_FRACTION);
        long grossReturn = computeSellReturn(stock, quantity);
        if (grossReturn > maxBranksImpact) {
            throw new IllegalStateException(
                "Trade too large. Reduce quantity — max single-trade impact is 10% of pool depth."
            );
        }

        // Wash-trade penalty: selling within window of last buy
        long now = System.currentTimeMillis();
        long netReturn = grossReturn;
        if (holding.getLastBuyAt() > 0 && (now - holding.getLastBuyAt()) < WASH_TRADE_WINDOW_MS) {
            long penalty = (long) Math.floor(grossReturn * WASH_TRADE_PENALTY);
            netReturn = grossReturn - penalty;
        }

        // Execute: credit Branks, update pool, update holding
        user.setBranks(user.getBranks() + netReturn);
        stock.setLiquidityBranks(stock.getLiquidityBranks() - grossReturn);
        stock.setLiquidityShares(stock.getLiquidityShares() + quantity);

        holding.setSharesOwned(holding.getSharesOwned() - quantity);
        holding.setLastTradedAt(now);

        com.ansh.lyfegameserver.service.activity.ActivityService.record(
            user, "TRADE", netReturn, "Sold " + quantity + " " + stock.getTicker());

        stock.appendPrice(stock.getCurrentPrice());
        stockRepository.save(stock);
        Users saved = userRepository.save(user);

        double avgPrice = (double) netReturn / quantity;
        broadcaster.broadcast(stock);

        return new TradeResult(saved, quantity, avgPrice, -netReturn, stock.getCurrentPrice());
    }

    // ─── Limit Orders ───────────────────────────────────────────────────────

    public LimitOrder placeLimitOrder(String clerkId, String stockId, String action,
                                      long quantity, double limitPrice) {
        requireUser(clerkId);
        requireStock(stockId);
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive.");
        if (limitPrice <= 0) throw new IllegalArgumentException("Limit price must be positive.");
        LimitOrder order = new LimitOrder(stockId, clerkId, action, quantity, limitPrice);
        return limitOrderRepository.save(order);
    }

    public LimitOrder cancelLimitOrder(String clerkId, String orderId) {
        LimitOrder order = limitOrderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        if (!order.getClerkId().equals(clerkId)) {
            throw new IllegalStateException("Not your order.");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new IllegalStateException("Order is not pending.");
        }
        order.setStatus("CANCELLED");
        return limitOrderRepository.save(order);
    }

    public List<LimitOrder> getUserPendingOrders(String clerkId) {
        return limitOrderRepository.findByClerkIdAndStatus(clerkId, "PENDING");
    }

    // ─── IPO ────────────────────────────────────────────────────────────────

    public Stock createIPO(String clerkId, IPOCreateRequest req) {
        Users user = requireUser(clerkId);

        if (user.getBranks() < IPO_MIN_BRANKS) {
            throw new IllegalStateException(
                "Need " + IPO_MIN_BRANKS + " Branks to launch an IPO. You have: " + user.getBranks()
            );
        }
        if (user.getIpo() != null) {
            // If the stock was manually deleted from the DB, clear the stale reference
            if (stockRepository.findById(user.getIpo().getStockId()).isEmpty()) {
                user.setIpo(null);
                userRepository.save(user);
            } else {
                throw new IllegalStateException("You have already launched a company.");
            }
        }
        if (stockRepository.findByTicker(req.ticker().toUpperCase()).isPresent()) {
            throw new IllegalStateException("Ticker '" + req.ticker() + "' is already taken.");
        }
        if (req.publicFloatPct() < 1 || req.publicFloatPct() > 99) {
            throw new IllegalArgumentException("Public float must be between 1% and 99%.");
        }

        long publicShares = (long) Math.floor(req.totalSupply() * req.publicFloatPct() / 100.0);
        long founderShares = req.totalSupply() - publicShares;

        // Pool seeded with: publicShares in pool, initial price sets Branks reserve
        long initialPoolBranks = publicShares * req.initialPricePerShare();

        // Deduct IPO cost from founder's balance (they fund the initial liquidity)
        if (user.getBranks() < initialPoolBranks) {
            throw new IllegalStateException(
                "Insufficient Branks to fund initial liquidity. Required: " + initialPoolBranks
            );
        }
        user.setBranks(user.getBranks() - initialPoolBranks);

        Stock stock = new Stock(
            req.ticker().toUpperCase(), req.name(), clerkId, false,
            initialPoolBranks, publicShares, req.totalSupply(), founderShares, 0
        );
        stock.setSector(req.sector());
        Stock saved = stockRepository.save(stock);

        user.setIpo(new UserIPO(saved.getId(), founderShares));
        userRepository.save(user);

        return saved;
    }

    // ─── Dilution ────────────────────────────────────────────────────────────

    public TradeResult dilute(String clerkId, String stockId, long quantity) {
        Users user = requireUser(clerkId);
        Stock stock = requireStock(stockId);

        if (!clerkId.equals(stock.getFounderClerkId())) {
            throw new IllegalStateException("Only the founder can dilute shares.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive.");
        }
        if (quantity > stock.getFounderSharesRetained()) {
            throw new IllegalStateException(
                "Insufficient retained shares. Retained: " + stock.getFounderSharesRetained()
            );
        }

        // Rate-limit dilutions to curb pool-draining / price manipulation.
        long now = System.currentTimeMillis();
        if (stock.getLastDilutedAt() > 0) {
            long elapsed = now - stock.getLastDilutedAt();
            if (elapsed < TRADE_COOLDOWN_MS) {
                long remainingSec = (TRADE_COOLDOWN_MS - elapsed + 999) / 1000;
                throw new IllegalStateException(
                    "Dilution on cooldown. Try again in " + remainingSec + "s."
                );
            }
        }

        long maxBranksImpact = (long) (stock.getLiquidityBranks() * MAX_POOL_IMPACT_FRACTION);
        long grossReturn = computeSellReturn(stock, quantity);
        if (grossReturn > maxBranksImpact) {
            throw new IllegalStateException(
                "Dilution too large. Max single-trade impact is 10% of pool depth."
            );
        }

        // AMM sell: retained shares go into pool, founder receives Branks
        user.setBranks(user.getBranks() + grossReturn);
        stock.setLiquidityBranks(stock.getLiquidityBranks() - grossReturn);
        stock.setLiquidityShares(stock.getLiquidityShares() + quantity);
        stock.setFounderSharesRetained(stock.getFounderSharesRetained() - quantity);
        stock.setLastDilutedAt(now);

        stock.appendPrice(stock.getCurrentPrice());
        stockRepository.save(stock);
        Users saved = userRepository.save(user);
        broadcaster.broadcast(stock);

        double avgPrice = (double) grossReturn / quantity;
        return new TradeResult(saved, quantity, avgPrice, grossReturn, stock.getCurrentPrice());
    }

    // ─── Govt Bond Yield ────────────────────────────────────────────────────

    /**
     * Called by the scheduler. Pays yield to all holders of the govt bond.
     * One game-day = 24 real-seconds. Yield = yieldRateBps / 10000 of holding value.
     */
    public void payGovtBondYield() {
        Optional<Stock> bondOpt = stockRepository.findByTicker("BOND");
        if (bondOpt.isEmpty()) return;
        Stock bond = bondOpt.get();

        double yieldFraction = bond.getYieldRateBps() / 10000.0;
        double pricePerShare = bond.getCurrentPrice();

        List<Users> allUsers = userRepository.findAll();
        for (Users user : allUsers) {
            for (UserStockHolding h : user.getStockHoldings()) {
                if (!h.getStockId().equals(bond.getId())) continue;
                if (h.getSharesOwned() <= 0) continue;

                // Cap the principal that earns yield so payouts plateau rather than
                // compounding without bound for large holders.
                double holdingValue = h.getSharesOwned() * pricePerShare;
                double eligibleValue = Math.min(holdingValue, BOND_MAX_YIELDABLE_VALUE);
                long yield = (long) Math.floor(eligibleValue * yieldFraction);
                if (yield > 0) {
                    user.setBranks(user.getBranks() + yield);
                    user.setLifetimeBondYield(user.getLifetimeBondYield() + yield);
                    com.ansh.lyfegameserver.service.activity.ActivityService.record(
                        user, "BOND_YIELD", yield, "Government bond yield");
                    userRepository.save(user);
                }
                break;
            }
        }

        bond.setLastYieldPaidAt(System.currentTimeMillis());
        stockRepository.save(bond);
    }

    /**
     * Auto-invests up to {@code branksToInvest} Branks into the government bond by buying whole
     * shares at the current price. Best-effort: returns the Branks actually spent, or 0 if the
     * bond is missing, the amount is too small, or the buy is rejected (cooldown / pool impact).
     */
    public long reinvestIntoBond(String clerkId, long branksToInvest) {
        if (branksToInvest <= 0) return 0L;
        Optional<Stock> bondOpt = stockRepository.findByTicker("BOND");
        if (bondOpt.isEmpty()) return 0L;

        Stock bond = bondOpt.get();
        double price = bond.getCurrentPrice();
        if (price <= 0) return 0L;

        long quantity = (long) Math.floor(branksToInvest / price);
        if (quantity <= 0) return 0L;

        try {
            TradeResult result = executeBuy(clerkId, bond.getId(), quantity);
            return result.branksDelta(); // for a buy this is the positive cost
        } catch (Exception e) {
            return 0L; // cooldown / pool-impact / insufficient funds — skip gracefully
        }
    }

    // ─── Limit Order Fill (called by scheduler) ──────────────────────────────

    public void processLimitOrders() {
        List<LimitOrder> pending = limitOrderRepository.findByStatus("PENDING");
        for (LimitOrder order : pending) {
            Optional<Stock> stockOpt = stockRepository.findById(order.getStockId());
            if (stockOpt.isEmpty()) continue;
            Stock stock = stockOpt.get();
            double currentPrice = stock.getCurrentPrice();

            boolean shouldFill = "BUY".equals(order.getAction())
                ? currentPrice <= order.getLimitPrice()
                : currentPrice >= order.getLimitPrice();

            if (!shouldFill) continue;

            try {
                if ("BUY".equals(order.getAction())) {
                    executeBuy(order.getClerkId(), order.getStockId(), order.getQuantity());
                } else {
                    executeSell(order.getClerkId(), order.getStockId(), order.getQuantity());
                }
                order.setStatus("FILLED");
            } catch (Exception e) {
                // If trade fails (e.g. insufficient funds), cancel the order
                order.setStatus("CANCELLED");
            }
            limitOrderRepository.save(order);
        }
    }

    // ─── AMM Formulas ────────────────────────────────────────────────────────

    /**
     * Constant-product AMM buy cost.
     * Given buying `shares` from a pool (B, S):
     *   cost = B * shares / (S - shares)
     */
    public long computeBuyCost(Stock stock, long shares) {
        if (shares >= stock.getLiquidityShares()) {
            throw new IllegalArgumentException("Not enough shares in pool.");
        }
        double b = stock.getLiquidityBranks();
        double s = stock.getLiquidityShares();
        return (long) Math.ceil(b * shares / (s - shares));
    }

    /**
     * Constant-product AMM sell return.
     * Given selling `shares` into a pool (B, S):
     *   return = B * shares / (S + shares)
     */
    public long computeSellReturn(Stock stock, long shares) {
        double b = stock.getLiquidityBranks();
        double s = stock.getLiquidityShares();
        return (long) Math.floor(b * shares / (s + shares));
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /**
     * Founders may not buy or sell their own company's stock on the open market — that
     * (combined with dilute) enabled self price-manipulation. They can still raise Branks
     * by diluting retained shares via {@link #dilute}.
     */
    private void rejectFounderSelfTrade(String clerkId, Stock stock) {
        if (clerkId.equals(stock.getFounderClerkId())) {
            throw new IllegalStateException(
                "Founders cannot trade their own company's stock. Use dilute to sell retained shares."
            );
        }
    }

    private void enforceCooldown(UserStockHolding holding, String ticker) {
        long now = System.currentTimeMillis();
        if (holding.getLastTradedAt() > 0) {
            long elapsed = now - holding.getLastTradedAt();
            if (elapsed < TRADE_COOLDOWN_MS) {
                long remainingSec = (TRADE_COOLDOWN_MS - elapsed) / 1000 + 1;
                throw new IllegalStateException(
                    "Cooldown active on " + ticker + ". Wait " + remainingSec + "s."
                );
            }
        }
    }

    private UserStockHolding getOrCreateHolding(Users user, String stockId) {
        for (UserStockHolding h : user.getStockHoldings()) {
            if (h.getStockId().equals(stockId)) return h;
        }
        UserStockHolding h = new UserStockHolding(stockId, 0L, 0L, 0L);
        user.getStockHoldings().add(h);
        return h;
    }

    private StockInfo toStockInfo(Stock stock) {
        double current = stock.getCurrentPrice();
        List<Double> snaps = stock.getHourlySnapshots();
        double ref = snaps.isEmpty() ? current : snaps.get(0);
        double change = current - ref;
        double changePct = ref > 0 ? (change / ref) * 100.0 : 0.0;
        return new StockInfo(
            stock.getId(), stock.getTicker(), stock.getName(),
            stock.isGovtBond(), stock.getSector(), current,
            change, changePct,
            stock.getLiquidityBranks(), stock.getLiquidityShares(),
            stock.getTotalSupply(), stock.getYieldRateBps(),
            stock.getPriceHistory(),
            stock.getFounderClerkId(),
            stock.getFounderSharesRetained()
        );
    }

    /** Called every 60 real-seconds (= 1 game-hour) by the scheduler. */
    public void snapshotHourlyPrices() {
        List<Stock> stocks = stockRepository.findAll();
        for (Stock stock : stocks) {
            List<Double> snaps = stock.getHourlySnapshots();
            snaps.add(stock.getCurrentPrice());
            if (snaps.size() > 24) snaps.remove(0);
            stock.setHourlySnapshots(snaps);
        }
        stockRepository.saveAll(stocks);
    }

    private Users requireUser(String clerkId) {
        return userRepository.findByClerkId(clerkId)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Stock requireStock(String stockId) {
        return stockRepository.findById(stockId)
            .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + stockId));
    }

    /** Value object returned by executeBuy/executeSell. */
    public record TradeResult(
        Users user,
        long sharesTransacted,
        double avgPrice,
        long branksDelta,
        double newPoolPrice
    ) {}
}
