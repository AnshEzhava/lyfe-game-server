package com.ansh.lyfegameserver.dto.job;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Details of the user's currently active job, including accumulated wages.
 */
@Getter
@AllArgsConstructor
public class ActiveJobInfo {
    private String jobId;
    private String jobName;
    private long startedAt;
    private long lastClaimedAt;

    /** Branks accumulated since last claim, based on elapsed real time */
    private long pendingWages;

    /** Effective hourly rate (Branks/game-hr) at current intelligence */
    private double effectiveHourlyRate;
}
