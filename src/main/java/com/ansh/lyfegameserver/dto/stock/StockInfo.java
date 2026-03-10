package com.ansh.lyfegameserver.dto.stock;

import java.util.List;

import com.ansh.lyfegameserver.data.Sector;

public record StockInfo(
    String id,
    String ticker,
    String name,
    boolean govtBond,
    Sector sector,
    double currentPrice,
    double priceChange24h,
    double priceChangePct24h,
    long liquidityBranks,
    long liquidityShares,
    long totalSupply,
    int yieldRateBps,
    List<Double> priceHistory,
    String founderClerkId,
    long founderSharesRetained
) {}
