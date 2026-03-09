package com.ansh.lyfegameserver.dto.job;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents one job entry in the available-jobs list, including
 * the user's effective wage at their current intelligence level.
 */
@Getter
@AllArgsConstructor
public class JobInfo {
    private String id;
    private String name;
    private String description;
    private int baseHourlyRate;
    private int minIntelligence;
    private boolean partTime;

    /**
     * Effective hourly rate for this user:
     *   effectiveRate = baseHourlyRate * (intelligence / 50.0)
     * Units: Branks per game-hour (= Branks per real-minute)
     */
    private double effectiveHourlyRate;

    /** Whether the user's intelligence meets the minimum requirement */
    private boolean eligible;
}
