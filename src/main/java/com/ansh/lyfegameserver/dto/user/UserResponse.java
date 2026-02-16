package com.ansh.lyfegameserver.dto.user;

import com.ansh.lyfegameserver.data.UserStats;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class UserResponse {
    
    private String id;
    private String displayName;
    private Long balance;
    private UserStats stats;
}
