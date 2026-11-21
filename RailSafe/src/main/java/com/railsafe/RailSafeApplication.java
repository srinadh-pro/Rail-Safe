package com.railsafe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the RailSafe Spring Boot application.
 *
 * Run this class from IntelliJ IDEA, or run "mvn spring-boot:run" in the project folder.
 * The REST API and the static frontend are both served from http://localhost:8080.
 */
@SpringBootApplication
@EnableScheduling
public class RailSafeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RailSafeApplication.class, args);
    }
}
