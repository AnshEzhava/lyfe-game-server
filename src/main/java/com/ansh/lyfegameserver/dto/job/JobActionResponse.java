package com.ansh.lyfegameserver.dto.job;

import com.ansh.lyfegameserver.dto.user.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Response for POST /api/user/jobs/start and POST /api/user/jobs/quit */
@Getter
@AllArgsConstructor
public class JobActionResponse {
    private int responseCode;
    private String responseMessage;
    private UserResponse user;
}
