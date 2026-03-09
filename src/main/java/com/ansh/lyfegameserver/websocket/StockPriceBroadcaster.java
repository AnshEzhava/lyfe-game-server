package com.ansh.lyfegameserver.websocket;

import com.ansh.lyfegameserver.data.Stock;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StockPriceBroadcaster {

    /** Minimum interval between broadcasts for the same stock (1 real-second). */
    private static final long BROADCAST_DEBOUNCE_MS = 1_000L;

    private final SimpMessagingTemplate messagingTemplate;

    /** Last broadcast timestamp per stockId */
    private final Map<String, Long> lastBroadcastAt = new ConcurrentHashMap<>();

    public StockPriceBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /** Broadcasts a price tick to subscribers; debounced to max 1 per second per stock. */
    public void broadcast(Stock stock) {
        long now = System.currentTimeMillis();
        long last = lastBroadcastAt.getOrDefault(stock.getId(), 0L);
        if (now - last < BROADCAST_DEBOUNCE_MS) return;

        lastBroadcastAt.put(stock.getId(), now);
        PriceTick tick = new PriceTick(
            stock.getId(),
            stock.getTicker(),
            stock.getCurrentPrice(),
            stock.getLiquidityBranks(),
            stock.getLiquidityShares(),
            now
        );
        messagingTemplate.convertAndSend("/topic/price/" + stock.getId(), tick);
    }

    public record PriceTick(
        String stockId,
        String ticker,
        double price,
        long liquidityBranks,
        long liquidityShares,
        long timestamp
    ) {}
}
