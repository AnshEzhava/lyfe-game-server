package com.ansh.lyfegameserver.dto.education;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents one course in the available-courses list.
 */
@Getter
@AllArgsConstructor
public class CourseInfo {
    private String id;
    private String name;
    private String description;
    private long cost;

    /** Real-world duration in seconds (1 real second = 1 game minute) */
    private long durationSeconds;

    /** Intelligence points gained on completion */
    private int intelligenceReward;

    /** Minimum intelligence required to enroll */
    private int minIntelligence;

    /** Whether the user's intelligence meets the requirement */
    private boolean eligible;
}
