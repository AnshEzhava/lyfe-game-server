package com.ansh.lyfegameserver.data;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserCourse {

    private String courseId;
    private Long enrolledAt;
    private Long completesAt;
    private boolean rewardClaimed;
}
