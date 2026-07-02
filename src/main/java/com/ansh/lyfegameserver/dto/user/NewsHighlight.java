package com.ansh.lyfegameserver.dto.user;

/** A single notable news item that affected the player's holdings while they were away. */
public record NewsHighlight(String headline, String ticker, double impactPct) {}
