package com.ansh.lyfegameserver.data;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UserStats {
    
    private Integer intelligence; // 0-100
    
    public UserStats() {
        this.intelligence = 50;
    }
}
