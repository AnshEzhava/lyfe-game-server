package com.ansh.lyfegameserver.service.stock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs background scheduled tasks for the stock market:
 * - Limit order fills: every 1 real-second
 * - Govt bond yield payout: every 24 real-seconds (= 1 game-day)
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

    /** 1 game-day = 24 real-seconds */
    @Scheduled(fixedRate = 24_000)
    public void payBondYield() {
        stockService.payGovtBondYield();
    }
}
