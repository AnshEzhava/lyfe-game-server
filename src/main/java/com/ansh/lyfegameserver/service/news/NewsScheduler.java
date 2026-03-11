package com.ansh.lyfegameserver.service.news;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Triggers news evaluation every 120 real-seconds (= 2 game-hours). */
@Component
public class NewsScheduler {

    private final NewsService newsService;

    public NewsScheduler(NewsService newsService) {
        this.newsService = newsService;
    }

    @Scheduled(fixedRate = 120_000)
    public void evaluateAndPublish() {
        newsService.evaluateAndPublish();
    }
}
