package com.ansh.lyfegameserver.controller.stock;

import com.ansh.lyfegameserver.data.LimitOrder;
import com.ansh.lyfegameserver.data.Stock;
import com.ansh.lyfegameserver.dto.stock.*;
import com.ansh.lyfegameserver.dto.user.UserResponse;
import com.ansh.lyfegameserver.service.stock.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private static final Logger logger = LoggerFactory.getLogger(StockController.class);

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    public ResponseEntity<StockQuoteResponse> getAllStocks() {
        List<StockInfo> stocks = stockService.getAllStocks();
        return ResponseEntity.ok(new StockQuoteResponse(0, "Success", stocks));
    }

    @GetMapping("/{stockId}")
    public ResponseEntity<StockInfo> getStock(@PathVariable String stockId) {
        try {
            return ResponseEntity.ok(stockService.getStock(stockId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/trade")
    public ResponseEntity<TradeResponse> trade(
        @RequestBody TradeRequest req,
        JwtAuthenticationToken auth
    ) {
        String clerkId = auth.getName();
        try {
            StockService.TradeResult result = "BUY".equalsIgnoreCase(req.action())
                ? stockService.executeBuy(clerkId, req.stockId(), req.quantity())
                : stockService.executeSell(clerkId, req.stockId(), req.quantity());

            UserResponse userResp = new UserResponse(
                result.user().getId(),
                result.user().getDisplayName(),
                result.user().getBranks(),
                result.user().getStats()
            );
            return ResponseEntity.ok(new TradeResponse(
                0, "Trade executed.",
                result.sharesTransacted(),
                result.avgPrice(),
                result.branksDelta(),
                result.newPoolPrice(),
                userResp
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                new TradeResponse(1, e.getMessage(), 0, 0, 0, 0, null)
            );
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(
                new TradeResponse(1, e.getMessage(), 0, 0, 0, 0, null)
            );
        } catch (RuntimeException e) {
            logger.error("Trade error for {}: {}", clerkId, e.getMessage());
            return ResponseEntity.internalServerError().body(
                new TradeResponse(1, "Internal error.", 0, 0, 0, 0, null)
            );
        }
    }

    @PostMapping("/limit")
    public ResponseEntity<LimitOrderResponse> placeLimitOrder(
        @RequestBody LimitOrderRequest req,
        JwtAuthenticationToken auth
    ) {
        String clerkId = auth.getName();
        try {
            LimitOrder order = stockService.placeLimitOrder(
                clerkId, req.stockId(), req.action(), req.quantity(), req.limitPrice()
            );
            return ResponseEntity.ok(new LimitOrderResponse(
                0, "Limit order placed.",
                order.getId(), order.getStockId(), order.getAction(),
                order.getQuantity(), order.getLimitPrice(), order.getStatus()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                new LimitOrderResponse(1, e.getMessage(), null, null, null, 0, 0, null)
            );
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/limit/{orderId}")
    public ResponseEntity<LimitOrderResponse> cancelLimitOrder(
        @PathVariable String orderId,
        JwtAuthenticationToken auth
    ) {
        String clerkId = auth.getName();
        try {
            LimitOrder order = stockService.cancelLimitOrder(clerkId, orderId);
            return ResponseEntity.ok(new LimitOrderResponse(
                0, "Order cancelled.",
                order.getId(), order.getStockId(), order.getAction(),
                order.getQuantity(), order.getLimitPrice(), order.getStatus()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(
                new LimitOrderResponse(1, e.getMessage(), null, null, null, 0, 0, null)
            );
        }
    }

    @GetMapping("/limit")
    public ResponseEntity<List<LimitOrder>> getPendingOrders(JwtAuthenticationToken auth) {
        return ResponseEntity.ok(stockService.getUserPendingOrders(auth.getName()));
    }

    @GetMapping("/portfolio")
    public ResponseEntity<PortfolioResponse> getPortfolio(JwtAuthenticationToken auth) {
        try {
            return ResponseEntity.ok(stockService.getPortfolio(auth.getName()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{stockId}/dilute")
    public ResponseEntity<?> dilute(
        @PathVariable String stockId,
        @RequestBody DiluteRequest req,
        JwtAuthenticationToken auth
    ) {
        String clerkId = auth.getName();
        try {
            StockService.TradeResult result = stockService.dilute(clerkId, stockId, req.quantity());
            UserResponse userResp = new UserResponse(
                result.user().getId(),
                result.user().getDisplayName(),
                result.user().getBranks(),
                result.user().getStats()
            );
            return ResponseEntity.ok(new TradeResponse(
                0, "Dilution complete.",
                result.sharesTransacted(),
                result.avgPrice(),
                result.branksDelta(),
                result.newPoolPrice(),
                userResp
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(
                java.util.Map.of("responseMessage", e.getMessage())
            );
        } catch (RuntimeException e) {
            logger.error("Dilute error for {}: {}", clerkId, e.getMessage());
            return ResponseEntity.internalServerError().body(
                java.util.Map.of("responseMessage", "Internal error.")
            );
        }
    }

    @PostMapping("/ipo")
    public ResponseEntity<?> createIPO(
        @RequestBody IPOCreateRequest req,
        JwtAuthenticationToken auth
    ) {
        String clerkId = auth.getName();
        try {
            Stock stock = stockService.createIPO(clerkId, req);
            return ResponseEntity.ok(stockService.getStock(stock.getId()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(
                java.util.Map.of("responseMessage", e.getMessage())
            );
        } catch (RuntimeException e) {
            logger.error("IPO error for {}: {}", clerkId, e.getMessage());
            return ResponseEntity.internalServerError().body(
                java.util.Map.of("responseMessage", "Internal error: " + e.getMessage())
            );
        }
    }
}
