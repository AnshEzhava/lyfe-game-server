package com.ansh.lyfegameserver.repository;

import com.ansh.lyfegameserver.data.Stock;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface StockRepository extends MongoRepository<Stock, String> {
    Optional<Stock> findByTicker(String ticker);
}
