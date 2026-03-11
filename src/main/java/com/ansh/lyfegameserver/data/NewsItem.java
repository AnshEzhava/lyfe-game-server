package com.ansh.lyfegameserver.data;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter @Setter
@NoArgsConstructor
@Document(collection = "news")
public class NewsItem {

    @Id
    private String id;

    private String headline;
    private String body;

    /** "COMPANY" or "SECTOR" */
    private String targetType;

    /** stockId for company events, Sector enum name for sector events */
    private String targetId;

    /** e.g. "Lyfe Corp" or "Technology & IT" */
    private String targetLabel;

    /** Company ticker (null for sector events) */
    private String ticker;

    /** Signed impact percentage, e.g. -8.5 or +6.2 */
    private double impactPct;

    private long publishedAt;

    /** publishedAt + 1_440_000 ms (= 1 game-day) */
    private long expiresAt;

    public NewsItem(String headline, String body, String targetType, String targetId,
                    String targetLabel, String ticker, double impactPct) {
        this.headline = headline;
        this.body = body;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetLabel = targetLabel;
        this.ticker = ticker;
        this.impactPct = impactPct;
        this.publishedAt = System.currentTimeMillis();
        this.expiresAt = this.publishedAt + 1_440_000L;
    }
}
