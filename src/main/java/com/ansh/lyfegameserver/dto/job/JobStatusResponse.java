package com.ansh.lyfegameserver.dto.job;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Full response for GET /api/user/jobs.
 * Contains the user's current job state plus the complete job catalogue.
 */
@Getter
@AllArgsConstructor
public class JobStatusResponse {
    private int responseCode;
    private String responseMessage;

    /** The currently held job, or null if unemployed */
    private ActiveJobInfo activeJob;

    /** Complete catalogue of all jobs with eligibility flags */
    private List<JobInfo> availableJobs;
}
