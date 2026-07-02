package com.ansh.lyfegameserver.data;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
@Document(collection = "users")
public class Users {

    @Id
    private String id;

    @Indexed(unique = true)
    private String clerkId;
    private String displayName;
    private Long branks;
    private UserStats stats;

    /** The user's current active job, or null if unemployed */
    private UserJob activeJob;

    /** The user's current active course enrollment, or null if not enrolled */
    private UserCourse activeCourse;

    /** Stock holdings — shares owned per stock */
    private List<UserStockHolding> stockHoldings = new ArrayList<>();

    /** IPO the user has founded, or null if none */
    private UserIPO ipo;

    /** Start timestamp of the current tax year (ms). */
    private long taxAnchorAt;

    /** Net worth captured at the start of the current tax year; the tax baseline. */
    private long taxAnchorNetWorth;

    /** Cumulative Branks paid in tax over the user's lifetime. */
    private long totalTaxPaid;

    public Users(String clerkId, String displayName){
        this.clerkId = clerkId;
        this.displayName = displayName;
        this.branks = 1000L;
        this.stats = new UserStats();
        this.activeJob = null;
        this.activeCourse = null;
        this.stockHoldings = new ArrayList<>();
        this.ipo = null;
        this.taxAnchorAt = System.currentTimeMillis();
        this.taxAnchorNetWorth = this.branks;
        this.totalTaxPaid = 0L;
    }
}
