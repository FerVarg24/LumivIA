package com.lumivia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LumiviaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LumiviaBackendApplication.class, args);
    }
}
