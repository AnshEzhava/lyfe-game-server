package com.ansh.lyfegameserver.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserStockHolding {

    private String stockId;
    private long sharesOwned;

    /** Epoch ms of the user's last trade on this stock — used for cooldown enforcement */
    private long lastTradedAt;

    /** Epoch ms of the most recent BUY on this stock — used for wash-trade detection */
    private long lastBuyAt;
}
