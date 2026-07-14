package com.ansh.lyfegameserver.dto.casino;

import com.ansh.lyfegameserver.dto.user.UserResponse;

import java.util.List;

/** Result of a casino bet. reels is populated for SLOTS, empty otherwise. */
public record CasinoPlayResponse(
    int responseCode,
    String responseMessage,
    String game,
    boolean win,
    long bet,
    long payout,      // total Branks returned (0 on a loss)
    long netDelta,    // payout - bet
    String outcome,   // human-readable result (e.g. "HEADS", "5", "WIN")
    List<String> reels,
    UserResponse user
) {}
