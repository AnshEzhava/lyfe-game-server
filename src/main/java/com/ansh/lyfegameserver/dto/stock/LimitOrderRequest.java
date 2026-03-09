package com.ansh.lyfegameserver.dto.stock;

public record LimitOrderRequest(
    String stockId,
    String action,  // "BUY" or "SELL"
    long quantity,
    double limitPrice
) {}
