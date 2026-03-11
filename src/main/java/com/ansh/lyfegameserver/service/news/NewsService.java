package com.ansh.lyfegameserver.service.news;

import com.ansh.lyfegameserver.data.NewsItem;
import com.ansh.lyfegameserver.data.Sector;
import com.ansh.lyfegameserver.data.Stock;
import com.ansh.lyfegameserver.repository.NewsItemRepository;
import com.ansh.lyfegameserver.repository.StockRepository;
import com.ansh.lyfegameserver.websocket.StockPriceBroadcaster;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class NewsService {

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

    /** Map from event enum string → human-readable description for the Mistral prompt */
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

    private static final double STOCK_EVENT_PROBABILITY = 0.15;
    private static final double SECTOR_EVENT_PROBABILITY = 0.05;
    private static final double REVERSAL_THRESHOLD_UP   = 20.0;
    private static final double REVERSAL_THRESHOLD_DOWN = -25.0;

    private final StockRepository stockRepository;
    private final NewsItemRepository newsItemRepository;
    private final MistralClient mistralClient;
    private final StockPriceBroadcaster broadcaster;
    private final SimpMessagingTemplate messagingTemplate;
    private final Random rng = new Random();

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
     * Called every 120 real-seconds by the scheduler.
     * Evaluates each non-bond stock for a 15% chance news event,
     * and separately evaluates a 5% chance of a sector-wide event.
     */
    public void evaluateAndPublish() {
        List<Stock> stocks = stockRepository.findAll();

        // Per-stock events
        for (Stock stock : stocks) {
            if (stock.isGovtBond()) continue;
            if (rng.nextDouble() > STOCK_EVENT_PROBABILITY) continue;
            publishCompanyEvent(stock);
        }

        // Sector-wide event (5% chance per evaluation cycle)
        if (rng.nextDouble() <= SECTOR_EVENT_PROBABILITY) {
            publishSectorEvent(stocks);
        }
    }

    // ─── Company event ─────────────────────────────────────────────────────

    private void publishCompanyEvent(Stock stock) {
        boolean positive = determineDirection(stock);
        double impactPct = (3 + rng.nextDouble() * 7) * (positive ? 1 : -1);
        String eventType = positive
            ? POS_EVENTS[rng.nextInt(POS_EVENTS.length)]
            : NEG_EVENTS[rng.nextInt(NEG_EVENTS.length)];

        String userPrompt = String.format(
            "Company: %s (%s), Sector: %s%nEvent: %s%nSentiment: %s%nWrite the news article.",
            stock.getName(), stock.getTicker(),
            stock.getSector() != null ? stock.getSector().name() : "General",
            EVENT_DESC.getOrDefault(eventType, eventType),
            positive ? "positive" : "negative"
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

    private void publishSectorEvent(List<Stock> allStocks) {
        // Collect non-bond stocks with a non-null sector
        List<Stock> eligibleStocks = allStocks.stream()
            .filter(s -> !s.isGovtBond() && s.getSector() != null)
            .toList();

        if (eligibleStocks.isEmpty()) return;

        // Pick a random sector that has at least one stock
        List<Sector> sectors = eligibleStocks.stream()
            .map(Stock::getSector)
            .distinct()
            .collect(Collectors.toList());
        Sector sector = sectors.get(rng.nextInt(sectors.size()));

        boolean positive = rng.nextBoolean();
        double impactPct = (3 + rng.nextDouble() * 4) * (positive ? 1 : -1);
        String eventType = positive
            ? POS_SECTOR_EVENTS[rng.nextInt(POS_SECTOR_EVENTS.length)]
            : NEG_SECTOR_EVENTS[rng.nextInt(NEG_SECTOR_EVENTS.length)];

        String sectorLabel = sectorLabel(sector);
        String userPrompt = String.format(
            "Sector: %s%nEvent: %s%nSentiment: %s%nWrite the news article.",
            sectorLabel,
            EVENT_DESC.getOrDefault(eventType, eventType),
            positive ? "positive" : "negative"
        );

        MistralClient.MistralArticle article = mistralClient.generateArticle(userPrompt);

        // Apply impact to all stocks in the sector
        List<Stock> sectorStocks = eligibleStocks.stream()
            .filter(s -> sector.equals(s.getSector()))
            .toList();
        for (Stock s : sectorStocks) {
            applyPoolImpact(s, impactPct);
        }

        NewsItem item = new NewsItem(
            article.headline(), article.body(),
            "SECTOR", sector.name(), sectorLabel,
            null, impactPct
        );
        newsItemRepository.save(item);
        messagingTemplate.convertAndSend("/topic/news", item);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    /**
     * Determines whether the event should be positive or negative.
     * Forced negative if stock is up >20% in last 24h; forced positive if down >25%.
     */
    private boolean determineDirection(Stock stock) {
        double changePct = compute24hChangePct(stock);
        if (changePct > REVERSAL_THRESHOLD_UP)   return false;
        if (changePct < REVERSAL_THRESHOLD_DOWN)  return true;
        return rng.nextBoolean();
    }

    private double compute24hChangePct(Stock stock) {
        List<Double> snaps = stock.getHourlySnapshots();
        if (snaps == null || snaps.isEmpty()) return 0.0;
        double ref = snaps.get(0);
        double current = stock.getCurrentPrice();
        return ref > 0 ? ((current - ref) / ref) * 100.0 : 0.0;
    }

    /** Directly manipulates the AMM Branks reserve and broadcasts the updated price. */
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
