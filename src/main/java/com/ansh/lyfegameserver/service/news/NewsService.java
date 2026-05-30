package com.ansh.lyfegameserver.service.news;

import com.ansh.lyfegameserver.data.NewsItem;
import com.ansh.lyfegameserver.data.Sector;
import com.ansh.lyfegameserver.data.Stock;
import com.ansh.lyfegameserver.repository.NewsItemRepository;
import com.ansh.lyfegameserver.repository.StockRepository;
import com.ansh.lyfegameserver.websocket.StockPriceBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class NewsService {

    private static final Logger logger = LoggerFactory.getLogger(NewsService.class);

    // ─── Event type catalogues ─────────────────────────────────────────────

    private static final String[] NEG_EVENTS = {
        "REGULATORY_PROBE", "EXEC_SCANDAL", "MASS_LAYOFF",
        "DATA_BREACH", "EARNINGS_MISS", "COMPETITOR_THREAT"
    };

    private static final String[] POS_EVENTS = {
        "NEW_CONTRACT", "PRODUCT_LAUNCH", "PARTNERSHIP",
        "EARNINGS_BEAT", "EXEC_HIRE", "MARKET_EXPANSION"
    };

    private static final String[] NEG_SECTOR_EVENTS = {
        "SECTOR_REGULATION", "SECTOR_DISRUPTION"
    };

    private static final String[] POS_SECTOR_EVENTS = {
        "SECTOR_BOOM", "SECTOR_SUBSIDY"
    };

    private static final Map<String, String> EVENT_DESC = Map.ofEntries(
        Map.entry("REGULATORY_PROBE",  "under investigation by market regulators"),
        Map.entry("EXEC_SCANDAL",      "hit by a major executive misconduct scandal"),
        Map.entry("MASS_LAYOFF",       "announcing large-scale employee layoffs"),
        Map.entry("DATA_BREACH",       "suffering a significant data security breach"),
        Map.entry("EARNINGS_MISS",     "missing quarterly earnings expectations badly"),
        Map.entry("COMPETITOR_THREAT", "facing a serious new competitive threat"),
        Map.entry("NEW_CONTRACT",      "winning a major new business contract"),
        Map.entry("PRODUCT_LAUNCH",    "launching an exciting new product"),
        Map.entry("PARTNERSHIP",       "entering a lucrative strategic partnership"),
        Map.entry("EARNINGS_BEAT",     "beating quarterly earnings expectations"),
        Map.entry("EXEC_HIRE",         "hiring a highly regarded executive"),
        Map.entry("MARKET_EXPANSION",  "expanding into a promising new market"),
        Map.entry("SECTOR_REGULATION", "facing sweeping new regulatory restrictions"),
        Map.entry("SECTOR_DISRUPTION", "disrupted by an emerging technology wave"),
        Map.entry("SECTOR_BOOM",       "experiencing rapid growth and investor enthusiasm"),
        Map.entry("SECTOR_SUBSIDY",    "receiving significant government subsidies and support")
    );

    /** Stock up >20% in 24h → 70% chance negative news */
    private static final double COMPANY_BIAS_UP_THRESHOLD  = 20.0;
    /** Stock down >25% in 24h → 70% chance positive news */
    private static final double COMPANY_BIAS_DOWN_THRESHOLD = -25.0;
    /** Sector avg up >15% in 24h → 70% chance negative news */
    private static final double SECTOR_BIAS_UP_THRESHOLD   = 15.0;
    /** Sector avg down >20% in 24h → 70% chance positive news */
    private static final double SECTOR_BIAS_DOWN_THRESHOLD = -20.0;
    private static final double BIAS_PROBABILITY           = 0.70;
    private static final double SECTOR_IMPACT_VARIANCE     = 2.0;

    /**
     * Roughly 1 in 4 ticks produces a sector story instead of a company story.
     * With 12 ticks/game-day → ~3 sector + ~9 company headlines.
     */
    private static final double SECTOR_STORY_CHANCE = 0.25;

    private final StockRepository stockRepository;
    private final NewsItemRepository newsItemRepository;
    private final MistralClient mistralClient;
    private final StockPriceBroadcaster broadcaster;
    private final SimpMessagingTemplate messagingTemplate;
    private final Random rng = new Random();

    private int tickCount = 0;

    public NewsService(StockRepository stockRepository,
                       NewsItemRepository newsItemRepository,
                       MistralClient mistralClient,
                       StockPriceBroadcaster broadcaster,
                       SimpMessagingTemplate messagingTemplate) {
        this.stockRepository = stockRepository;
        this.newsItemRepository = newsItemRepository;
        this.mistralClient = mistralClient;
        this.broadcaster = broadcaster;
        this.messagingTemplate = messagingTemplate;
    }

    // ─── Public API ────────────────────────────────────────────────────────

    public List<NewsItem> getRecentNews() {
        return newsItemRepository.findTop20ByOrderByPublishedAtDesc();
    }

    /**
     * Called every ~2 game-hours by the scheduler.
     * Publishes one headline: either a sector-wide story or a company-specific one.
     */
    public void publishNextStory() {
        List<Stock> eligible = stockRepository.findAll().stream()
            .filter(s -> !s.isGovtBond() && s.getSector() != null)
            .collect(Collectors.toList());

        if (eligible.isEmpty()) return;

        tickCount++;
        boolean doSector = rng.nextDouble() < SECTOR_STORY_CHANCE;

        if (doSector) {
            List<Sector> sectors = eligible.stream()
                .map(Stock::getSector)
                .distinct()
                .collect(Collectors.toList());
            Sector chosen = sectors.get(rng.nextInt(sectors.size()));
            publishSectorEvent(eligible, chosen);
        } else {
            Collections.shuffle(eligible, rng);
            publishCompanyEvent(eligible.get(0));
        }

        logger.debug("News tick #{}: published {} story", tickCount, doSector ? "sector" : "company");
    }

    // ─── Company event ─────────────────────────────────────────────────────

    private void publishCompanyEvent(Stock stock) {
        double changePct = compute24hChangePct(stock);
        boolean positive = determineCompanyDirection(changePct);
        double impactPct = (3.0 + rng.nextDouble() * 7.0) * (positive ? 1 : -1);
        String eventType = positive
            ? POS_EVENTS[rng.nextInt(POS_EVENTS.length)]
            : NEG_EVENTS[rng.nextInt(NEG_EVENTS.length)];

        String userPrompt = String.format(
            "Sentiment: %s%nCompany: %s (%s), Sector: %s%nEvent: %s is %s%nWrite the news article.",
            positive ? "BULLISH" : "BEARISH",
            stock.getName(), stock.getTicker(),
            stock.getSector() != null ? sectorLabel(stock.getSector()) : "General",
            stock.getName(),
            EVENT_DESC.getOrDefault(eventType, eventType)
        );

        MistralClient.MistralArticle article = mistralClient.generateArticle(userPrompt);
        applyPoolImpact(stock, impactPct);

        NewsItem item = new NewsItem(
            article.headline(), article.body(),
            "COMPANY", stock.getId(), stock.getName(),
            stock.getTicker(), impactPct
        );
        newsItemRepository.save(item);
        messagingTemplate.convertAndSend("/topic/news", item);
    }

    // ─── Sector event ──────────────────────────────────────────────────────

    private void publishSectorEvent(List<Stock> eligible, Sector sector) {
        List<Stock> sectorStocks = eligible.stream()
            .filter(s -> sector.equals(s.getSector()))
            .toList();

        if (sectorStocks.isEmpty()) return;

        double avgChangePct = sectorStocks.stream()
            .mapToDouble(this::compute24hChangePct)
            .average()
            .orElse(0.0);

        boolean positive = determineSectorDirection(avgChangePct);
        double basePct   = (3.0 + rng.nextDouble() * 4.0) * (positive ? 1 : -1);
        String eventType = positive
            ? POS_SECTOR_EVENTS[rng.nextInt(POS_SECTOR_EVENTS.length)]
            : NEG_SECTOR_EVENTS[rng.nextInt(NEG_SECTOR_EVENTS.length)];

        String sectorName = sectorLabel(sector);
        String userPrompt = String.format(
            "Sentiment: %s%nSector: %s%nEvent: The %s sector is %s%nWrite the news article.",
            positive ? "BULLISH" : "BEARISH",
            sectorName,
            sectorName,
            EVENT_DESC.getOrDefault(eventType, eventType)
        );

        MistralClient.MistralArticle article = mistralClient.generateArticle(userPrompt);

        for (Stock s : sectorStocks) {
            double variance = (rng.nextDouble() * SECTOR_IMPACT_VARIANCE * 2) - SECTOR_IMPACT_VARIANCE;
            applyPoolImpact(s, basePct + variance);
        }

        NewsItem item = new NewsItem(
            article.headline(), article.body(),
            "SECTOR", sector.name(), sectorName,
            null, basePct
        );
        newsItemRepository.save(item);
        messagingTemplate.convertAndSend("/topic/news", item);
    }

    // ─── Direction logic ───────────────────────────────────────────────────

    private boolean determineCompanyDirection(double changePct) {
        if (changePct > COMPANY_BIAS_UP_THRESHOLD)   return rng.nextDouble() > BIAS_PROBABILITY;
        if (changePct < COMPANY_BIAS_DOWN_THRESHOLD) return rng.nextDouble() < BIAS_PROBABILITY;
        return rng.nextBoolean();
    }

    private boolean determineSectorDirection(double avgChangePct) {
        if (avgChangePct > SECTOR_BIAS_UP_THRESHOLD)   return rng.nextDouble() > BIAS_PROBABILITY;
        if (avgChangePct < SECTOR_BIAS_DOWN_THRESHOLD) return rng.nextDouble() < BIAS_PROBABILITY;
        return rng.nextBoolean();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private double compute24hChangePct(Stock stock) {
        List<Double> snaps = stock.getHourlySnapshots();
        if (snaps == null || snaps.isEmpty()) return 0.0;
        double ref = snaps.get(0);
        double current = stock.getCurrentPrice();
        return ref > 0 ? ((current - ref) / ref) * 100.0 : 0.0;
    }

    private void applyPoolImpact(Stock stock, double impactPct) {
        long newBranks = Math.round(stock.getLiquidityBranks() * (1.0 + impactPct / 100.0));
        if (newBranks < 1) newBranks = 1;
        stock.setLiquidityBranks(newBranks);
        stockRepository.save(stock);
        broadcaster.broadcast(stock);
    }

    private String sectorLabel(Sector sector) {
        return switch (sector) {
            case IT            -> "Technology & IT";
            case FINANCE       -> "Finance";
            case HEALTHCARE    -> "Healthcare";
            case ENERGY        -> "Energy";
            case AGRICULTURE   -> "Agriculture";
            case TRADE         -> "Trade & Commerce";
            case SCIENCE       -> "Science & Research";
            case ENTERTAINMENT -> "Entertainment";
            case MANUFACTURING -> "Manufacturing";
            case REAL_ESTATE   -> "Real Estate";
        };
    }
}
