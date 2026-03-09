package com.ansh.lyfegameserver.dto.stock;

import com.ansh.lyfegameserver.dto.user.UserResponse;

public record TradeResponse(
    int responseCode,
    String responseMessage,
    long sharesTransacted,
    double avgPrice,
    long branksDelta,
    double newPoolPrice,
    UserResponse user
) {}
