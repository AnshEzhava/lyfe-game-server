package com.ansh.lyfegameserver.data;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
@NoArgsConstructor
@Document(collection = "stocks")
public class Stock {

    @Id
    private String id;

    @Indexed(unique = true)
    private String ticker;

    private String name;

    /** null for the government bond */
    private String founderClerkId;

    private boolean govtBond;

    private Sector sector;

    /** AMM liquidity pool — Branks reserve */
    private long liquidityBranks;

    /** AMM liquidity pool — shares reserve */
    private long liquidityShares;

    private long totalSupply;

    /** Shares the founder still holds outside the pool */
    private long founderSharesRetained;

    private long createdAt;

    /** Yield rate in basis points (100 bps = 1%). Only meaningful for govt bond. */
    private int yieldRateBps;

    /** Timestamp of the last yield payout. Only meaningful for govt bond. */
    private long lastYieldPaidAt;

    /** Timestamp of the last founder dilution. Used to rate-limit dilutions. */
    private long lastDilutedAt;

    /** Rolling price history, capped at 100 entries (most recent last). */
    private List<Double> priceHistory = new ArrayList<>();

    /**
     * Hourly price snapshots, capped at 24 entries (most recent last).
     * One entry is appended every 60 real-seconds (= 1 game-hour) by the scheduler.
     * Used to compute the 24-game-hour price change.
     */
    private List<Double> hourlySnapshots = new ArrayList<>();

    public Stock(String ticker, String name, String founderClerkId, boolean govtBond,
                 long liquidityBranks, long liquidityShares, long totalSupply,
                 long founderSharesRetained, int yieldRateBps) {
        this.ticker = ticker;
        this.name = name;
        this.founderClerkId = founderClerkId;
        this.govtBond = govtBond;
        this.liquidityBranks = liquidityBranks;
        this.liquidityShares = liquidityShares;
        this.totalSupply = totalSupply;
        this.founderSharesRetained = founderSharesRetained;
        this.yieldRateBps = yieldRateBps;
        this.createdAt = System.currentTimeMillis();
        this.lastYieldPaidAt = System.currentTimeMillis();
    }

    /** Current price = branks reserve / shares reserve */
    public double getCurrentPrice() {
        if (liquidityShares == 0) return 0.0;
        return (double) liquidityBranks / (double) liquidityShares;
    }

    /** Appends a price sample; drops the oldest entry when over 100. */
    public void appendPrice(double price) {
        if (priceHistory.size() >= 100) {
            priceHistory.remove(0);
        }
        priceHistory.add(price);
    }
}
