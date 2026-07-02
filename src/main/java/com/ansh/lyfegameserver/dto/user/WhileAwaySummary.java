package com.ansh.lyfegameserver.dto.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** The "while you were away" report shown when a player returns to the game. */
@Getter @AllArgsConstructor
public class WhileAwaySummary {

    private int responseCode;
    private String responseMessage;

    /** False when the player was away less than a game-day — the client suppresses the modal. */
    private boolean hasSummary;

    private long gameDaysAway;
    private long wagesEarned;
    private long bondYield;
    private long taxPaid;          // positive = amount deducted
    private long netWorthChange;   // signed delta since last seen

    private boolean autoClaimed;
    private long autoReinvested;   // Branks moved into the government bond

    private List<NewsHighlight> newsHighlights;

    /** Name of a course that finished and is ready to claim, or null. */
    private String courseReady;

    /** Updated user state after any auto-claim / auto-reinvest. */
    private UserResponse user;
}
