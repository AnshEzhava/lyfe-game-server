package com.ansh.lyfegameserver.data;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

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

    public Users(String clerkId, String displayName){
        this.clerkId = clerkId;
        this.displayName = displayName;
        this.branks = 1000L;
        this.stats = new UserStats();
        this.activeJob = null;
        this.activeCourse = null;
    }
}
