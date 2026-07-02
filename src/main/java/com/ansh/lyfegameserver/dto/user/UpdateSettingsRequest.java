package com.ansh.lyfegameserver.dto.user;

/** Request body for updating a player's AFK automation settings. */
public record UpdateSettingsRequest(boolean autoClaimWages, boolean autoReinvest) {}
