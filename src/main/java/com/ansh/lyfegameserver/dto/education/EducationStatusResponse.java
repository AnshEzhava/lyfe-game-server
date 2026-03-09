package com.ansh.lyfegameserver.dto.education;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Full response for GET /api/user/education.
 */
@Getter
@AllArgsConstructor
public class EducationStatusResponse {
    private int responseCode;
    private String responseMessage;

    /** Current enrollment, or null if not studying */
    private ActiveCourseInfo activeCourse;

    /** Complete course catalogue with eligibility flags */
    private List<CourseInfo> availableCourses;
}
