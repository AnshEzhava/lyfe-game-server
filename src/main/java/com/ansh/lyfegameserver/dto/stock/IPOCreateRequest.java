package com.ansh.lyfegameserver.dto.stock;

import com.ansh.lyfegameserver.data.Sector;

public record IPOCreateRequest(
    String name,
    String ticker,
    Sector sector,
    long totalSupply,
    long initialPricePerShare,
    int publicFloatPct
) {}
