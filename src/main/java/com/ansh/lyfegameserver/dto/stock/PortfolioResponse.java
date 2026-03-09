package com.ansh.lyfegameserver.dto.stock;

import java.util.List;

public record PortfolioResponse(
    int responseCode,
    String responseMessage,
    List<HoldingInfo> holdings,
    long netWorth,
    long branks
) {
    public record HoldingInfo(
        String stockId,
        String ticker,
        String name,
        long sharesOwned,
        double currentPrice,
        long currentValue
    ) {}
}
