package com.ansh.lyfegameserver.dto.stock;

public record StockQuoteResponse(
    int responseCode,
    String responseMessage,
    java.util.List<StockInfo> stocks
) {}
