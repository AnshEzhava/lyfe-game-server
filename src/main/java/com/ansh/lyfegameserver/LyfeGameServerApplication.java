package com.ansh.lyfegameserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LyfeGameServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LyfeGameServerApplication.class, args);
    }

}
