package com.ansh.lyfegameserver.controller.user;

import com.ansh.lyfegameserver.data.Users;
import com.ansh.lyfegameserver.dto.education.EducationActionResponse;
import com.ansh.lyfegameserver.dto.education.EducationStatusResponse;
import com.ansh.lyfegameserver.dto.education.EnrollCourseRequest;
import com.ansh.lyfegameserver.dto.job.ClaimWageResponse;
import com.ansh.lyfegameserver.dto.job.JobActionResponse;
import com.ansh.lyfegameserver.dto.job.JobStatusResponse;
import com.ansh.lyfegameserver.dto.job.StartJobRequest;
import com.ansh.lyfegameserver.dto.user.BalanceResponse;
import com.ansh.lyfegameserver.dto.user.CreateUserRequest;
import com.ansh.lyfegameserver.dto.user.CreateUserResponse;
import com.ansh.lyfegameserver.dto.user.StudyResponse;
import com.ansh.lyfegameserver.dto.user.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ansh.lyfegameserver.service.user.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/find")
    public ResponseEntity<StudyResponse> findUser(JwtAuthenticationToken auth) {
        String clerkId = auth.getName();
        logger.info("Finding user with clerkId: {}", clerkId);
        return userService.findByClerkId(clerkId)
            .map(user -> {
                UserResponse userResponse = new UserResponse(
                    user.getId(),
                    user.getDisplayName(),
                    user.getBranks(),
                    user.getStats()
                );
                return ResponseEntity.ok(new StudyResponse(0, "Success", userResponse));
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/add")
    public ResponseEntity<CreateUserResponse> addUser(
        @RequestBody CreateUserRequest createUserRequest,
        JwtAuthenticationToken auth
    ) {
        String clerkId = auth.getName();
        try {
            userService.createUser(clerkId, createUserRequest.getDisplayName());
            return ResponseEntity.ok(new CreateUserResponse(0, "Display Name Successfully Set!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance(JwtAuthenticationToken auth) {
        String clerkId = auth.getName();
        Optional<Users> user = userService.findByClerkId(clerkId);
        return user.map(u -> ResponseEntity.ok(
            new BalanceResponse(0, "Successfully Fetched Balance", u.getBranks())
        )).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs")
    public ResponseEntity<JobStatusResponse> getJobs(JwtAuthenticationToken auth) {
        try {
            JobStatusResponse res = userService.getJobStatus(auth.getName());
            return ResponseEntity.ok(res);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/jobs/start")
    public ResponseEntity<JobActionResponse> startJob(
        @RequestBody StartJobRequest req,
        JwtAuthenticationToken auth
    ) {
        try {
            Users user = userService.startJob(auth.getName(), req.getJobId());
            return ResponseEntity.ok(new JobActionResponse(0, "Job started successfully.", toUserResponse(user)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new JobActionResponse(1, e.getMessage(), null));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new JobActionResponse(1, e.getMessage(), null));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/jobs/quit")
    public ResponseEntity<JobActionResponse> quitJob(JwtAuthenticationToken auth) {
        try {
            Users user = userService.quitJob(auth.getName());
            return ResponseEntity.ok(new JobActionResponse(0, "Job quit successfully.", toUserResponse(user)));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new JobActionResponse(1, e.getMessage(), null));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/jobs/claim")
    public ResponseEntity<ClaimWageResponse> claimWage(JwtAuthenticationToken auth) {
        try {
            UserService.ClaimWageResult result = userService.claimWage(auth.getName());
            return ResponseEntity.ok(new ClaimWageResponse(0, "Wages claimed: " + result.wages() + " Branks",
                result.wages(), toUserResponse(result.user())));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ClaimWageResponse(1, e.getMessage(), 0, null));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/education")
    public ResponseEntity<EducationStatusResponse> getEducation(JwtAuthenticationToken auth) {
        try {
            EducationStatusResponse res = userService.getEducationStatus(auth.getName());
            return ResponseEntity.ok(res);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/education/enroll")
    public ResponseEntity<EducationActionResponse> enrollCourse(
        @RequestBody EnrollCourseRequest req,
        JwtAuthenticationToken auth
    ) {
        try {
            Users user = userService.enrollCourse(auth.getName(), req.getCourseId());
            return ResponseEntity.ok(new EducationActionResponse(0, "Enrolled successfully.", toUserResponse(user)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new EducationActionResponse(1, e.getMessage(), null));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new EducationActionResponse(1, e.getMessage(), null));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/education/complete")
    public ResponseEntity<EducationActionResponse> completeCourse(JwtAuthenticationToken auth) {
        try {
            Users user = userService.completeCourse(auth.getName());
            return ResponseEntity.ok(new EducationActionResponse(0, "Course completed! Intelligence increased.", toUserResponse(user)));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new EducationActionResponse(1, e.getMessage(), null));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private UserResponse toUserResponse(Users user) {
        return new UserResponse(user.getId(), user.getDisplayName(), user.getBranks(), user.getStats());
    }
}
