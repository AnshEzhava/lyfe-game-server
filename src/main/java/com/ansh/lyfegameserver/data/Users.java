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

    public Users(String clerkId, String displayName){
        this.clerkId = clerkId;
        this.displayName = displayName;
        this.branks = 1000L;
        this.stats = new UserStats();
        this.activeJob = null;
        this.activeCourse = null;
        this.stockHoldings = new ArrayList<>();
        this.ipo = null;
    }
}
