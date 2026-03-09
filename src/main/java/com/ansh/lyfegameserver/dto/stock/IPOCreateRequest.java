package com.ansh.lyfegameserver.dto.stock;

public record IPOCreateRequest(
    String name,
    String ticker,
    long totalSupply,
    long initialPricePerShare,
    int publicFloatPct  // 1-99: percentage of supply sold to the public
) {}
