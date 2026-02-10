package com.ansh.lyfegameserver.repository;

import com.ansh.lyfegameserver.data.Users;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<Users, String> {
    Optional<Users> findByClerkId(String clerkId);
}
