package com.ansh.lyfegameserver.dto.user;

/** Current AFK automation settings for a player. */
public record SettingsResponse(int responseCode, String responseMessage,
                               boolean autoClaimWages, boolean autoReinvest) {}
