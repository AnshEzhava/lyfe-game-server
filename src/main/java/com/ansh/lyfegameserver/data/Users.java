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

    // ─── AFK / offline-loop state ─────────────────────────────────────────────

    /** Epoch ms the player was last seen (used to build the "while you were away" summary). */
    private long lastSeenAt;

    /** Branks balance captured at lastSeenAt; baseline for the return summary delta. */
    private long lastSeenBranks;

    /** Net worth captured at lastSeenAt; baseline for the return summary delta. */
    private long lastSeenNetWorth;

    /** Running total of wages ever earned. Delta since snapshot drives the summary. */
    private long lifetimeWagesEarned;

    /** Running total of bond yield ever received. Delta since snapshot drives the summary. */
    private long lifetimeBondYield;

    /** AFK automation preferences. */
    private UserSettings settings;

    /** Recent income/expense events, capped at {@link #MAX_ACTIVITY_EVENTS} (most recent last). */
    private List<ActivityEvent> recentActivity = new ArrayList<>();

    /** Maximum number of activity events retained per user. */
    public static final int MAX_ACTIVITY_EVENTS = 40;

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
        this.lastSeenAt = System.currentTimeMillis();
        this.lastSeenBranks = this.branks;
        this.lastSeenNetWorth = this.branks;
        this.lifetimeWagesEarned = 0L;
        this.lifetimeBondYield = 0L;
        this.settings = new UserSettings();
        this.recentActivity = new ArrayList<>();
    }
}
