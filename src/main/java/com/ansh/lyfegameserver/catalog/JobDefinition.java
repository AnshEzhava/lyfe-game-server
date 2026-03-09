package com.ansh.lyfegameserver.catalog;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JobDefinition {

    private final String id;
    private final String name;
    private final String description;

    /** Base wage in Branks per game-hour; actual wage is scaled by intelligence. */
    private final int baseHourlyRate;

    private final int minIntelligence;

    /** If true, can be worked while enrolled in a course. */
    private final boolean partTime;
}
