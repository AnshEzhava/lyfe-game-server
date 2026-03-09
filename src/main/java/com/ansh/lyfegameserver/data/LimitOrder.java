package com.ansh.lyfegameserver.data;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter @Setter
@NoArgsConstructor
@Document(collection = "limit_orders")
public class LimitOrder {

    @Id
    private String id;

    private String stockId;
    private String clerkId;
    private String action; // "BUY" or "SELL"
    private long quantity;
    private double limitPrice;
    private long placedAt;
    private String status; // "PENDING", "FILLED", "CANCELLED"

    public LimitOrder(String stockId, String clerkId, String action,
                      long quantity, double limitPrice) {
        this.stockId = stockId;
        this.clerkId = clerkId;
        this.action = action;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.placedAt = System.currentTimeMillis();
        this.status = "PENDING";
    }
}
