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

    public Users study(String clerkId, String courseId) {
        Users user = findByClerkId(clerkId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Cost and intelligence gain (can be customized based on courseId in future)
        Long studyCost = 100L;
        Integer intelligenceGain = 5;
        
        // Check if user has enough money
        if (user.getBranks() < studyCost) {
            throw new RuntimeException("Insufficient funds");
        }
        
        // Deduct cost
        user.setBranks(user.getBranks() - studyCost);
        
        // Increase intelligence (cap at 100)
        Integer currentIntelligence = user.getStats().getIntelligence();
        Integer newIntelligence = Math.min(currentIntelligence + intelligenceGain, 100);
        user.getStats().setIntelligence(newIntelligence);
        
        return userRepository.save(user);
    }
}
