package com.ansh.lyfegameserver.repository;

import com.ansh.lyfegameserver.data.LimitOrder;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface LimitOrderRepository extends MongoRepository<LimitOrder, String> {
    List<LimitOrder> findByStatus(String status);
    List<LimitOrder> findByClerkIdAndStatus(String clerkId, String status);
}
