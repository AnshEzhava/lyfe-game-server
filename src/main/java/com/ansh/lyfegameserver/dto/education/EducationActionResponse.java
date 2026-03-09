package com.ansh.lyfegameserver.dto.education;

import com.ansh.lyfegameserver.dto.user.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Response for POST /api/user/education/enroll and POST /api/user/education/complete */
@Getter
@AllArgsConstructor
public class EducationActionResponse {
    private int responseCode;
    private String responseMessage;
    private UserResponse user;
}
