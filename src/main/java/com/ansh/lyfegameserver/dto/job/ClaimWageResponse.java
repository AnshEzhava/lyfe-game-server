package com.ansh.lyfegameserver.dto.job;

import com.ansh.lyfegameserver.dto.user.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Response for POST /api/user/jobs/claim */
@Getter
@AllArgsConstructor
public class ClaimWageResponse {
    private int responseCode;
    private String responseMessage;
    private long wagesClaimed;
    private UserResponse user;
}
