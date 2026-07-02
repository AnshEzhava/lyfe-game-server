package com.ansh.lyfegameserver.repository;

import com.ansh.lyfegameserver.data.NewsItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NewsItemRepository extends MongoRepository<NewsItem, String> {
    List<NewsItem> findTop20ByOrderByPublishedAtDesc();

    List<NewsItem> findByPublishedAtGreaterThanEqualOrderByPublishedAtDesc(long publishedAt);
}
