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

    public Users(String clerkId, String displayName){
        this.clerkId = clerkId;
        this.displayName = displayName;
        this.branks = 1000L;
    }
}
