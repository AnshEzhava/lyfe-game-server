package com.ansh.lyfegameserver.controller.user;

import com.ansh.lyfegameserver.data.Users;
import com.ansh.lyfegameserver.dto.user.BalanceResponse;
import com.ansh.lyfegameserver.dto.user.CreateUserRequest;
import com.ansh.lyfegameserver.dto.user.CreateUserResponse;
import com.ansh.lyfegameserver.dto.user.FindUserResponse;
import org.apache.coyote.Response;
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
    public ResponseEntity<FindUserResponse> findUser(JwtAuthenticationToken auth) {
        String clerkId = auth.getName();
        logger.info("Finding user with clerkId: {}", clerkId);
        return userService.findByClerkId(clerkId)
                .map(user -> ResponseEntity.ok(new FindUserResponse(0, "Success", user.getDisplayName())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/add")
    public ResponseEntity<CreateUserResponse> addUser(@RequestBody CreateUserRequest createUserRequest, JwtAuthenticationToken auth) {
        String clerkId = auth.getName();
        try {
            userService.createUser(clerkId, createUserRequest.getDisplayName());
            return  ResponseEntity.ok(new CreateUserResponse(0, "Display Name Successfully Set!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance(JwtAuthenticationToken auth){
       String clerkId = auth.getName();
       Optional<Users> user = userService.findByClerkId(clerkId);
        return user.map(users -> ResponseEntity.ok(new BalanceResponse(0, "Successfully Fetched Balance", users.getBranks()))).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
