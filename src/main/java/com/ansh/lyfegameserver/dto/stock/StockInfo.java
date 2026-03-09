package com.ansh.lyfegameserver.dto.stock;

import java.util.List;

public record StockInfo(
    String id,
    String ticker,
    String name,
    boolean govtBond,
    double currentPrice,
    double priceChange24h,
    double priceChangePct24h,
    long liquidityBranks,
    long liquidityShares,
    long totalSupply,
    int yieldRateBps,
    List<Double> priceHistory
) {}
