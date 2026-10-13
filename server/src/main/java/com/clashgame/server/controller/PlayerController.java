package com.clashgame.server.controller;

import com.clashgame.server.model.Player;
import com.clashgame.server.service.BattleService;
import com.clashgame.server.service.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API controller for player operations.
 * Provides HTTP endpoints for operations that don't require real-time socket
 * communication (registration, profile viewing, leaderboard, etc.).
 * 
 * Note: Real-time game operations (battles, resource updates) use the
 * socket-based GameServer instead of REST endpoints.
 */
@RestController
@RequestMapping("/api/players")
@CrossOrigin(origins = "*")
public class PlayerController {

    @Autowired
    private PlayerService playerService;

    @Autowired
    private BattleService battleService;

    // ─── Authentication (alternative to socket-based login) ──────────

    /**
     * Register a new player account.
     * POST /api/players/register
     * Body: {"username": "...", "password": "..."}
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        if (username == null || password == null ||
            username.isBlank() || password.length() < 4) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid username or password (min 4 chars)"));
        }

        String json = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        String playerId = playerService.registerPlayer(json);

        if (playerId != null) {
            return ResponseEntity.ok(Map.of("playerId", playerId, "message", "Registration successful"));
        }
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Username already exists"));
    }

    /**
     * Authenticate and login a player.
     * POST /api/players/login
     * Body: {"username": "...", "password": "..."}
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String json = String.format("{\"username\":\"%s\",\"password\":\"%s\"}",
                request.get("username"), request.get("password"));
        String playerId = playerService.authenticatePlayer(json);

        if (playerId != null) {
            return ResponseEntity.ok(Map.of("playerId", playerId));
        }
        return ResponseEntity.status(401)
                .body(Map.of("error", "Invalid credentials"));
    }

    // ─── Player Profile ──────────────────────────────────────────────

    /**
     * Get a player's profile.
     * GET /api/players/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getPlayer(@PathVariable String id) {
        Player player = playerService.getPlayerById(id);
        if (player == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "id", player.getId(),
                "username", player.getUsername(),
                "level", player.getLevel(),
                "trophies", player.getTrophies(),
                "gold", player.getGold(),
                "elixir", player.getElixir(),
                "gems", player.getGems(),
                "online", player.isOnline()
        ));
    }

    // ─── Leaderboard ─────────────────────────────────────────────────

    /**
     * Get the global leaderboard (top players by trophies).
     * GET /api/players/leaderboard
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<String> getLeaderboard() {
        String leaderboard = playerService.getLeaderboard();
        return ResponseEntity.ok(leaderboard);
    }

    // ─── Battle History ──────────────────────────────────────────────

    /**
     * Get battle history for a player.
     * GET /api/players/{id}/battles
     */
    @GetMapping("/{id}/battles")
    public ResponseEntity<String> getBattleHistory(@PathVariable String id) {
        String history = battleService.getBattleHistory(id);
        return ResponseEntity.ok(history);
    }
}
