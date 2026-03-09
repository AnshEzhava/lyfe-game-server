package com.ansh.lyfegameserver.service.user;

import com.ansh.lyfegameserver.catalog.CourseDefinition;
import com.ansh.lyfegameserver.catalog.GameCatalog;
import com.ansh.lyfegameserver.catalog.JobDefinition;
import com.ansh.lyfegameserver.data.UserCourse;
import com.ansh.lyfegameserver.data.UserJob;
import com.ansh.lyfegameserver.data.Users;
import com.ansh.lyfegameserver.dto.education.ActiveCourseInfo;
import com.ansh.lyfegameserver.dto.education.CourseInfo;
import com.ansh.lyfegameserver.dto.education.EducationStatusResponse;
import com.ansh.lyfegameserver.dto.job.ActiveJobInfo;
import com.ansh.lyfegameserver.dto.job.JobInfo;
import com.ansh.lyfegameserver.dto.job.JobStatusResponse;
import com.ansh.lyfegameserver.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final GameCatalog gameCatalog;

    public UserService(UserRepository userRepository, GameCatalog gameCatalog) {
        this.userRepository = userRepository;
        this.gameCatalog = gameCatalog;
    }

    public Optional<Users> findByClerkId(String clerkId) {
        return userRepository.findByClerkId(clerkId);
    }

    public Users createUser(String clerkId, String displayName) {
        Users user = new Users(clerkId, displayName);
        return userRepository.save(user);
    }

    public JobStatusResponse getJobStatus(String clerkId) {
        Users user = requireUser(clerkId);
        int intelligence = user.getStats().getIntelligence();
        boolean isEnrolled = user.getActiveCourse() != null && !user.getActiveCourse().isRewardClaimed();

        List<JobInfo> jobs = gameCatalog.getAllJobs().stream()
            .map(j -> toJobInfo(j, intelligence))
            .toList();

        ActiveJobInfo activeJobInfo = null;
        if (user.getActiveJob() != null) {
            activeJobInfo = buildActiveJobInfo(user, intelligence);
        }

        return new JobStatusResponse(0, "Success", activeJobInfo, jobs);
    }

    /** Starts a job; auto-claims wages from any previous job. Part-time only if enrolled in a course. */
    public Users startJob(String clerkId, String jobId) {
        Users user = requireUser(clerkId);
        JobDefinition job = gameCatalog.findJob(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown job: " + jobId));

        int intelligence = user.getStats().getIntelligence();
        if (intelligence < job.getMinIntelligence()) {
            throw new IllegalStateException("Intelligence too low. Required: " + job.getMinIntelligence());
        }

        boolean isEnrolled = isActivelyEnrolled(user);
        if (isEnrolled && !job.isPartTime()) {
            throw new IllegalStateException("Cannot work a full-time job while enrolled in a course.");
        }

        // Auto-claim wages from previous job before switching
        if (user.getActiveJob() != null) {
            applyPendingWages(user, intelligence);
        }

        long now = System.currentTimeMillis();
        user.setActiveJob(new UserJob(jobId, now, now));
        return userRepository.save(user);
    }

    public Users quitJob(String clerkId) {
        Users user = requireUser(clerkId);
        if (user.getActiveJob() == null) {
            throw new IllegalStateException("You are not currently employed.");
        }

        applyPendingWages(user, user.getStats().getIntelligence());
        user.setActiveJob(null);
        return userRepository.save(user);
    }

    public ClaimWageResult claimWage(String clerkId) {
        Users user = requireUser(clerkId);
        if (user.getActiveJob() == null) {
            throw new IllegalStateException("You are not currently employed.");
        }

        int intelligence = user.getStats().getIntelligence();
        long wages = computePendingWages(user, intelligence);

        user.setBranks(user.getBranks() + wages);
        user.getActiveJob().setLastClaimedAt(System.currentTimeMillis());
        Users saved = userRepository.save(user);
        return new ClaimWageResult(saved, wages);
    }

    /** Value object returned by claimWage to avoid a second repository lookup. */
    public record ClaimWageResult(Users user, long wages) {}

    public EducationStatusResponse getEducationStatus(String clerkId) {
        Users user = requireUser(clerkId);
        int intelligence = user.getStats().getIntelligence();

        List<CourseInfo> courses = gameCatalog.getAllCourses().stream()
            .map(c -> toCourseInfo(c, intelligence))
            .toList();

        ActiveCourseInfo activeCourse = null;
        if (user.getActiveCourse() != null) {
            activeCourse = buildActiveCourseInfo(user.getActiveCourse());
        }

        return new EducationStatusResponse(0, "Success", activeCourse, courses);
    }

    /** Enrolls the user; deducts cost, auto-quits full-time job if enrolled. */
    public Users enrollCourse(String clerkId, String courseId) {
        Users user = requireUser(clerkId);
        CourseDefinition course = gameCatalog.findCourse(courseId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown course: " + courseId));

        if (isActivelyEnrolled(user)) {
            throw new IllegalStateException("Already enrolled in a course.");
        }

        int intelligence = user.getStats().getIntelligence();
        if (intelligence < course.getMinIntelligence()) {
            throw new IllegalStateException("Intelligence too low. Required: " + course.getMinIntelligence());
        }

        if (user.getBranks() < course.getCost()) {
            throw new IllegalStateException("Insufficient Branks. Cost: " + course.getCost());
        }

        // If user holds a full-time job, force them to quit before enrolling
        if (user.getActiveJob() != null) {
            JobDefinition currentJob = gameCatalog.findJob(user.getActiveJob().getJobId()).orElse(null);
            if (currentJob != null && !currentJob.isPartTime()) {
                // Auto-claim wages and quit the full-time job
                applyPendingWages(user, intelligence);
                user.setActiveJob(null);
            }
        }

        user.setBranks(user.getBranks() - course.getCost());

        long now = System.currentTimeMillis();
        long completesAt = now + (course.getDurationSeconds() * 1000L);
        user.setActiveCourse(new UserCourse(courseId, now, completesAt, false));

        return userRepository.save(user);
    }

    public Users completeCourse(String clerkId) {
        Users user = requireUser(clerkId);
        UserCourse enrollment = user.getActiveCourse();

        if (enrollment == null) {
            throw new IllegalStateException("No active course enrollment.");
        }
        if (enrollment.isRewardClaimed()) {
            throw new IllegalStateException("Course reward already claimed.");
        }
        if (System.currentTimeMillis() < enrollment.getCompletesAt()) {
            long remainingMs = enrollment.getCompletesAt() - System.currentTimeMillis();
            throw new IllegalStateException("Course not yet complete. " + remainingMs + "ms remaining.");
        }

        CourseDefinition course = gameCatalog.findCourse(enrollment.getCourseId())
            .orElseThrow(() -> new IllegalStateException("Course definition not found."));

        int currentIntelligence = user.getStats().getIntelligence();
        int newIntelligence = Math.min(currentIntelligence + course.getIntelligenceReward(), 100);
        user.getStats().setIntelligence(newIntelligence);
        enrollment.setRewardClaimed(true);

        // Clear the enrollment so they can start a new course
        user.setActiveCourse(null);

        return userRepository.save(user);
    }

    private Users requireUser(String clerkId) {
        return userRepository.findByClerkId(clerkId)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private boolean isActivelyEnrolled(Users user) {
        return user.getActiveCourse() != null && !user.getActiveCourse().isRewardClaimed();
    }

    /**
     * Computes pending wages without saving.
     * Formula: wages = floor(elapsedRealSeconds * (baseHourlyRate / 60.0) * (intelligence / 50.0))
     */
    private long computePendingWages(Users user, int intelligence) {
        UserJob job = user.getActiveJob();
        if (job == null) return 0L;

        JobDefinition def = gameCatalog.findJob(job.getJobId()).orElse(null);
        if (def == null) return 0L;

        long referenceTime = job.getLastClaimedAt() != null ? job.getLastClaimedAt() : job.getStartedAt();
        long elapsedMs = System.currentTimeMillis() - referenceTime;
        double elapsedSeconds = elapsedMs / 1000.0;

        // branks_per_real_second = (baseHourlyRate / 60.0) * (intelligence / 50.0)
        double branksPerSecond = (def.getBaseHourlyRate() / 60.0) * (intelligence / 50.0);
        return (long) Math.floor(elapsedSeconds * branksPerSecond);
    }

    private void applyPendingWages(Users user, int intelligence) {
        long wages = computePendingWages(user, intelligence);
        if (wages > 0) {
            user.setBranks(user.getBranks() + wages);
        }
        user.getActiveJob().setLastClaimedAt(System.currentTimeMillis());
    }

    private JobInfo toJobInfo(JobDefinition j, int intelligence) {
        double effective = j.getBaseHourlyRate() * (intelligence / 50.0);
        boolean eligible = intelligence >= j.getMinIntelligence();
        return new JobInfo(j.getId(), j.getName(), j.getDescription(),
            j.getBaseHourlyRate(), j.getMinIntelligence(), j.isPartTime(), effective, eligible);
    }

    private ActiveJobInfo buildActiveJobInfo(Users user, int intelligence) {
        UserJob job = user.getActiveJob();
        JobDefinition def = gameCatalog.findJob(job.getJobId()).orElse(null);
        if (def == null) return null;

        long pending = computePendingWages(user, intelligence);
        double effective = def.getBaseHourlyRate() * (intelligence / 50.0);
        long lastClaim = job.getLastClaimedAt() != null ? job.getLastClaimedAt() : job.getStartedAt();
        return new ActiveJobInfo(job.getJobId(), def.getName(), job.getStartedAt(), lastClaim, pending, effective);
    }

    private CourseInfo toCourseInfo(CourseDefinition c, int intelligence) {
        boolean eligible = intelligence >= c.getMinIntelligence();
        return new CourseInfo(c.getId(), c.getName(), c.getDescription(),
            c.getCost(), c.getDurationSeconds(), c.getIntelligenceReward(), c.getMinIntelligence(), eligible);
    }

    private ActiveCourseInfo buildActiveCourseInfo(UserCourse uc) {
        CourseDefinition def = gameCatalog.findCourse(uc.getCourseId()).orElse(null);
        String name = def != null ? def.getName() : uc.getCourseId();
        int reward = def != null ? def.getIntelligenceReward() : 0;

        long now = System.currentTimeMillis();
        long remaining = Math.max(0, uc.getCompletesAt() - now);
        boolean complete = now >= uc.getCompletesAt();

        return new ActiveCourseInfo(uc.getCourseId(), name, uc.getEnrolledAt(),
            uc.getCompletesAt(), remaining, complete, uc.isRewardClaimed(), reward);
    }
}
