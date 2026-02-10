package com.ansh.lyfegameserver.service.user;

import com.ansh.lyfegameserver.data.Users;
import com.ansh.lyfegameserver.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<Users> findByClerkId(String clerkId){
        return userRepository.findByClerkId(clerkId);
    }

    public Users createUser(String clerkId, String displayName){
        Users user = new Users(clerkId, displayName);
        return userRepository.save(user);
    }

    public Users updateBranks(String clerkId, Long branks){
        Users user = findByClerkId(clerkId)
                .orElseThrow(() -> new RuntimeException("Player not found"));
        user.setBranks(branks);
        return userRepository.save(user);
    }
}
