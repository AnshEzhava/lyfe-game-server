package com.ansh.lyfegameserver.dto.user;

import com.ansh.lyfegameserver.data.ActivityEvent;

import java.util.List;

/** Recent income / expense events for a player's activity feed. */
public record ActivityResponse(int responseCode, String responseMessage, List<ActivityEvent> events) {}
