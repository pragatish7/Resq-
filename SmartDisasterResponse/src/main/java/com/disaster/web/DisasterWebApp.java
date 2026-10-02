package com.disaster.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the disaster response system.
 * Boots the web server (Tomcat) and serves the static frontend plus the /api controllers.
 * The Swing UI remains available via the legacy Main class and is untouched.
 */
@SpringBootApplication
public class DisasterWebApp {

    public static void main(String[] args) {
        SpringApplication.run(DisasterWebApp.class, args);
    }
}
