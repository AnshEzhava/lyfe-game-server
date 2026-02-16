package com.ansh.lyfegameserver.dto.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class StudyResponse {
    private int responseCode;
    private String responseMessage;
    private UserResponse response;
}
