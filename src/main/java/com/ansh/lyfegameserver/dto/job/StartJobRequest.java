package com.ansh.lyfegameserver.dto.job;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Request body for POST /api/user/jobs/start */
@Getter
@AllArgsConstructor
public class StartJobRequest {
    private String jobId;
}
