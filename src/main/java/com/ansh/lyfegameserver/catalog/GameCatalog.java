package com.ansh.lyfegameserver.catalog;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * In-memory catalog of all jobs and courses.
 * Wage formula: effectiveHourlyRate = baseHourlyRate * (intelligence / 50.0); branks_per_real_sec = effectiveHourlyRate / 60.0
 */
@Component
public class GameCatalog {

    // Part-time (student-friendly)
    private static final List<JobDefinition> JOBS = List.of(
        new JobDefinition(
            "barista",
            "Barista",
            "Make coffee at a local café. Flexible hours, perfect for students.",
            50, 0, true
        ),
        new JobDefinition(
            "retail",
            "Retail Assistant",
            "Work the floor at a retail store. No experience required.",
            40, 0, true
        ),
        new JobDefinition(
            "delivery",
            "Delivery Driver",
            "Deliver packages on your own schedule.",
            60, 20, true
        ),

        // Full-time (not available during studies)
        new JobDefinition(
            "call-center",
            "Call Center Agent",
            "Handle customer support calls in a busy call center.",
            80, 30, false
        ),
        new JobDefinition(
            "admin",
            "Administrative Assistant",
            "Manage schedules and correspondence for a small firm.",
            100, 40, false
        ),
        new JobDefinition(
            "analyst",
            "Financial Analyst",
            "Analyze market data and produce reports for investment clients.",
            200, 65, false
        ),
        new JobDefinition(
            "engineer",
            "Software Engineer",
            "Build and maintain software systems at a tech company.",
            250, 70, false
        ),
        new JobDefinition(
            "manager",
            "Senior Manager",
            "Lead teams and strategy at a large organization.",
            350, 80, false
        )
    );

    private static final List<CourseDefinition> COURSES = List.of(

        new CourseDefinition(
            "bootcamp",
            "Online Bootcamp",
            "An intensive online crash course covering basic professional skills.",
            200L, 960L, 5, 0
        ),
        new CourseDefinition(
            "trade-school",
            "Trade School",
            "Practical vocational training for a skilled trade.",
            800L, 4320L, 10, 20
        ),
        new CourseDefinition(
            "bachelors",
            "Bachelor's Degree",
            "A four-year university degree unlocking professional careers.",
            3000L, 43200L, 20, 40
        ),
        new CourseDefinition(
            "masters",
            "Master's Degree",
            "Postgraduate specialisation for top-tier roles and maximum earning power.",
            8000L, 86400L, 30, 65
        )
    );

    private static final Map<String, JobDefinition> JOB_MAP =
        JOBS.stream().collect(Collectors.toMap(JobDefinition::getId, Function.identity()));

    private static final Map<String, CourseDefinition> COURSE_MAP =
        COURSES.stream().collect(Collectors.toMap(CourseDefinition::getId, Function.identity()));

    public List<JobDefinition> getAllJobs() {
        return JOBS;
    }

    public List<CourseDefinition> getAllCourses() {
        return COURSES;
    }

    public Optional<JobDefinition> findJob(String id) {
        return Optional.ofNullable(JOB_MAP.get(id));
    }

    public Optional<CourseDefinition> findCourse(String id) {
        return Optional.ofNullable(COURSE_MAP.get(id));
    }
}
