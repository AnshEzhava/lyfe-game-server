package com.ansh.lyfegameserver.dto.education;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Request body for POST /api/user/education/enroll */
@Getter
@AllArgsConstructor
public class EnrollCourseRequest {
    private String courseId;
}
