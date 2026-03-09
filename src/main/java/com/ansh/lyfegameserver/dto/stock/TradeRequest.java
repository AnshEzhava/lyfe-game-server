package com.ansh.lyfegameserver.dto.stock;

public record TradeRequest(
    String stockId,
    String action,  // "BUY" or "SELL"
    long quantity
) {}
