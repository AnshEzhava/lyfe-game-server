package com.ansh.lyfegameserver.dto.casino;

/**
 * A single casino bet.
 * game   — COINFLIP | DICE | SLOTS
 * bet    — Branks wagered
 * choice — game-specific pick (COINFLIP: HEADS/TAILS; DICE: HIGH/LOW or "1".."6"; SLOTS: ignored)
 */
public record CasinoPlayRequest(String game, long bet, String choice) {}
