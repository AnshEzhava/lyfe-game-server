package com.ansh.lyfegameserver.service.stock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs background scheduled tasks for the stock market. All intervals derive from the 60×
 * game clock (1 real-second = 1 game-minute):
 * - Limit order fills: every 1 real-second
 * - Hourly price snapshots: every 60 real-seconds (= 1 game-hour), used for 24h % change
 * - Govt bond yield payout: every 1,440,000 ms (= 24 real-minutes = 1 game-day)
 */
@Component
public class StockScheduler {

    private final StockService stockService;

    public StockScheduler(StockService stockService) {
        this.stockService = stockService;
    }

    @Scheduled(fixedRate = 1_000)
    public void fillLimitOrders() {
        stockService.processLimitOrders();
    }

    /** 1 game-hour = 60 real-seconds */
    @Scheduled(fixedRate = 60_000)
    public void snapshotHourlyPrices() {
        stockService.snapshotHourlyPrices();
    }

    /** 1 game-day = 24 real-minutes (60× clock: 24 game-hours × 60 real-sec) */
    @Scheduled(fixedRate = 1_440_000)
    public void payBondYield() {
        stockService.payGovtBondYield();
    }
}
