package com.ansh.lyfegameserver.dto.education;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Details of the user's currently active course enrollment.
 */
@Getter
@AllArgsConstructor
public class ActiveCourseInfo {
    private String courseId;
    private String courseName;
    private long enrolledAt;
    private long completesAt;

    /** Real-world epoch millis remaining until completion (0 if already complete) */
    private long remainingMs;

    /** Whether the course timer has elapsed (and reward can be claimed) */
    private boolean complete;

    /** Whether the completion reward has already been claimed */
    private boolean rewardClaimed;

    private int intelligenceReward;
}
