package com.ansh.lyfegameserver.service.news;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drip-feeds news throughout the game day.
 * Fires every 120 real-seconds (~2 game-hours), producing 1–2 headlines per tick.
 * With 12 ticks per game-day this yields ~10–15 headlines/day, spread evenly.
 */
@Component
public class NewsScheduler {

    private final NewsService newsService;

    public NewsScheduler(NewsService newsService) {
        this.newsService = newsService;
    }

    @Scheduled(fixedRate = 120_000, initialDelay = 30_000)
    public void tick() {
        newsService.publishNextStory();
    }
}
