package com.ansh.lyfegameserver.controller.news;

import com.ansh.lyfegameserver.data.NewsItem;
import com.ansh.lyfegameserver.service.news.NewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    public List<NewsItem> getRecentNews() {
        return newsService.getRecentNews();
    }
}
