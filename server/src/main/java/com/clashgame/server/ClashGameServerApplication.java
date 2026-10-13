package com.clashgame.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Clash of Clans Mini game server.
 * 
 * This Spring Boot application starts:
 * 1. The REST API (PlayerController)
 * 2. The Socket-based GameServer (for real-time communication)
 * 3. The ResourceGeneratorThread (background resource production)
 * 4. The GameStateManager (battle management and game loop)
 * 5. JPA/Database connectivity
 */
@SpringBootApplication
@EnableScheduling
public class ClashGameServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClashGameServerApplication.class, args);
        System.out.println("==============================================");
        System.out.println("  Clash of Clans Mini - Game Server Started   ");
        System.out.println("  REST API:   http://localhost:8080           ");
        System.out.println("  Game Port:  9090 (TCP Socket)              ");
        System.out.println("==============================================");
    }
}
