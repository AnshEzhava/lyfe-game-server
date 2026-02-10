package com.ansh.lyfegameserver.dto.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class CreateUserResponse {
    private int responseCode;
    private String responseMessage;
}
