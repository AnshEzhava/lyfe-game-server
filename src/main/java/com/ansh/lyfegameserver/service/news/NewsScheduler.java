package com.ansh.lyfegameserver.service.news;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Triggers a daily news batch once per game-day (every 1440 real-seconds = 24 real-minutes). */
@Component
public class NewsScheduler {

    private final NewsService newsService;

    public NewsScheduler(NewsService newsService) {
        this.newsService = newsService;
    }

    @Scheduled(fixedRate = 1_440_000)
    public void triggerDailyNews() {
        newsService.triggerDailyNews();
    }
}
