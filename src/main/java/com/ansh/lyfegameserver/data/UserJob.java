package com.ansh.lyfegameserver.data;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserJob {

    private String jobId;
    private Long startedAt;
    /** null means use startedAt as the reference for wage calculation */
    private Long lastClaimedAt;
}
