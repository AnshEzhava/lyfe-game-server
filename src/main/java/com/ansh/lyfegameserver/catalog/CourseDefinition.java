package com.ansh.lyfegameserver.catalog;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CourseDefinition {

    private final String id;
    private final String name;
    private final String description;

    /** Branks cost deducted on enrollment */
    private final long cost;

    /** Real-world duration in seconds (1 real second = 1 game minute). */
    private final long durationSeconds;

    /** Intelligence points gained upon completing this course */
    private final int intelligenceReward;

    private final int minIntelligence;
}
