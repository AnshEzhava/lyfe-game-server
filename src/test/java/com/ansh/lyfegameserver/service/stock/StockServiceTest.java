package com.ansh.lyfegameserver.service.stock;

import com.ansh.lyfegameserver.data.Stock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure AMM-math checks (no Spring context). Guards the crashed-pool recovery bug: a stock whose
 * Branks reserve fell to near zero must still accept a buy, otherwise its price is frozen at ~0.
 */
class StockServiceTest {

    private final StockService svc = new StockService(null, null, null, null);

    private static Stock crashedPool(long branks, long shares) {
        Stock s = new Stock();
        s.setLiquidityBranks(branks);
        s.setLiquidityShares(shares);
        return s;
    }

    @Test
    void crashedPoolStillAcceptsARecoveryBuy() {
        // B < 10: (long)(B*0.10) truncates to 0, which used to reject every buy.
        for (long b = 1; b < 10; b++) {
            Stock stock = crashedPool(b, 1500);
            long cost = svc.computeBuyCost(stock, 1);   // minimal 1-share buy
            long cap = svc.maxPoolImpact(stock);
            assertTrue(cost <= cap,
                "buy of 1 share (cost=" + cost + ") must be allowed on a crashed pool B=" + b
                    + " but cap=" + cap);
        }
    }

    @Test
    void buyRaisesPriceOnACrashedPool() {
        Stock stock = crashedPool(1, 1500);
        double before = stock.getCurrentPrice();
        long cost = svc.computeBuyCost(stock, 1);
        stock.setLiquidityBranks(stock.getLiquidityBranks() + cost);
        stock.setLiquidityShares(stock.getLiquidityShares() - 1);
        assertTrue(stock.getCurrentPrice() > before, "a buy must push price up, not leave it stuck");
    }
}
