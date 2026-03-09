package com.ansh.lyfegameserver.dto.stock;

public record LimitOrderResponse(
    int responseCode,
    String responseMessage,
    String orderId,
    String stockId,
    String action,
    long quantity,
    double limitPrice,
    String status
) {}
