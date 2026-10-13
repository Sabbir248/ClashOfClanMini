# Comprehensive Audit Report v2 — Multiplayer Online Strategy Game (Mini Clash of Clans)

> **Date:** 2025-01-XX  
> **Scope:** Full read-only codebase analysis across all 42 Java files, 7 FXML layouts, 1 CSS file, 1 SQL schema, 2 POM files, and 1 properties file.  
> **Priority Scale:** P0 = Critical/Broken, P1 = Major gap, P2 = Moderate, P3 = Minor/Polish

---

## Table of Contents

- [A. Missing Game Features](#a-missing-game-features)
- [B. Broken / Incomplete Flows](#b-broken--incomplete-flows)
- [C. Data Mismatches (Client ↔ Server ↔ DB)](#c-data-mismatches-client--server--db)
- [D. UI/UX Gaps](#d-uiux-gaps)
- [E. Missing OOP / Design Patterns](#e-missing-oop--design-patterns)
- [F. Missing Server Features](#f-missing-server-features)
- [G. Code Quality Issues](#g-code-quality-issues)

---

## A. Missing Game Features

### A1. [P0] No Building Upgrade Timer System
- **Files:** `server/src/main/java/com/clashgame/server/game/GameStateManager.java` (lines 85-87), `server/src/main/java/com/clashgame/server/service/PlayerService.java` (line ~320)
- **Description:** `GameStateManager.gameTick()` has three TODO placeholders: "Process building upgrade timers", "Process unit training timers", "Process scheduled events" — all are empty. `PlayerService.upgradeBuilding()` explicitly states "Complete upgrade instantly (no timer system in place)" and applies the upgrade immediately in the same call.
- **Impact:** Core Clash-of-Clans mechanic completely absent. Buildings upgrade instantaneously, eliminating strategic time management, gem-spend acceleration, and builder queue tension.
- **Fix:** Implement a `TimerService` (or scheduler in `GameStateManager`) that stores `upgradeStartTime` + `upgradeDurationSeconds` on each `Building` entity; tick every second to check completions; notify clients via `UPDATE_BASE` on completion. The `Building` model already has an `upgrading` boolean field — extend it with `upgradeStartedAt` and `upgradeFinishesAt` timestamps.

### A2. [P0] No Unit Training Timer System
- **Files:** `server/src/main/java/com/clashgame/server/service/PlayerService.java` (lines ~380-450), `server/src/main/java/com/clashgame/server/game/GameStateManager.java` (line 86)
- **Description:** `PlayerService.trainUnit()` immediately creates a `Unit` entity and adds it to the player's army in a single synchronous call. The `GameStateManager.gameTick()` TODO for "Process unit training timers" is empty.
- **Impact:** Training is instant — no queue, no wait time, no gem acceleration. Undermines resource balancing and strategic army planning.
- **Fix:** Add a `TrainingQueue` model with `unitType`, `startedAt`, `completesAt`; process in `gameTick()`; send `UNIT_TRAINED` messages upon completion.

### A3. [P1] Gem Currency Has No Use
- **Files:** `server/src/main/java/com/clashgame/server/model/Player.java` (line 44, 85), `client/src/main/resources/fxml/dashboard_screen.fxml` (gems label)
- **Description:** Players start with 50 gems. The gem count is displayed in the dashboard UI. However, there is zero server-side or client-side logic to spend, earn, or purchase gems. No gem shop, no gem-to-speed-upgrade, no gem-to-resource conversion exists anywhere.
- **Impact:** An entire currency is dead weight. Confuses players who see gems but can't use them.
- **Fix:** Implement gem spending for: instant upgrade completion, instant training, resource purchase, shield purchase. Add a `GemShopService` and corresponding UI screen.

### A4. [P1] Clan System Not Implemented
- **Files:** `server/src/main/java/com/clashgame/server/model/Player.java` (field `clanName`), `server/src/main/java/com/clashgame/server/repository/PlayerRepository.java` (`findByClanName` method), `client/src/main/java/com/clashgame/client/model/Player.java` (`clanName` property)
- **Description:** The `Player` entity has a `clanName` field and the repository has `findByClanName()`, but there are no clan management features: no clan creation, joining, leaving, clan chat, clan wars, donation, or clan-based leaderboards.
- **Impact:** Social feature completely stubbed. Field exists but is never written to or read in any meaningful way.
- **Fix:** Either implement a full `Clan` entity with join/leave/chat/donate/war features, or remove the `clanName` field and repository method to avoid confusion.

### A5. [P1] Laboratory Building Has No Functionality
- **Files:** `server/src/main/java/com/clashgame/server/model/BuildingType.java` (LABORATORY entry), `client/src/main/java/com/clashgame/client/controller/BaseController.java` (renderer at lines ~920-960), `client/src/main/java/com/clashgame/client/controller/BattleController.java` (`drawBattleLaboratory`)
- **Description:** The Laboratory building type is defined in the enum, can be placed and upgraded, and has detailed visual renderers on both base and battle screens. However, it provides no functionality — no troop research, no upgrade research, nothing.
- **Impact:** Misleading building that wastes 600 gold to place. In Clash of Clans, the Lab researches troop upgrades.
- **Fix:** Implement a `ResearchService` that allows researching unit level upgrades (increasing base stats) gated by Lab level. Add a "Research" UI screen accessible from the Lab building.

### A6. [P2] No Shield / Guard System
- **Description:** After being attacked, players should receive a temporary shield preventing further attacks. No shield mechanism exists — a player can be attacked repeatedly without cooldown protection (only attacker has a 5-min cooldown per defender in `MatchmakingService`).
- **Impact:** Players who go offline can be looted endlessly.
- **Fix:** Add `shieldExpiresAt` field to `Player`; grant 8/12/16 hour shields for 1/2/3 star losses; check in `MatchmakingService.findOpponent()`.

### A7. [P2] No Builder System
- **Description:** In Clash of Clans, builders limit concurrent construction. This project has no builder count or concurrent construction limits. All building placements and upgrades (which are instant anyway) have no concurrency constraint.
- **Fix:** Add `builderCount` to `Player` (default 2); track active builds; gate `placeBuilding()` and `upgradeBuilding()` on available builders.

### A8. [P3] No Battle Replay System
- **Description:** Battle results are stored in the `battles` table, but there is no mechanism to view or replay past battles. The BATTLE_UPDATE tick data is not persisted for replay.
- **Fix:** Store serialized battle ticks in a `battle_replay` table; add a "Battle Log" UI screen.

### A9. [P3] No Achievement / Quest System
- **Description:** No daily challenges, achievements, or quests exist. The game has no progression incentive beyond trophies and level.
- **Fix:** Add `Achievement` entity with unlock conditions; display progress in a new Achievement panel.

---

## B. Broken / Incomplete Flows

### B1. [P0] PvP Matchmaking Queue Never Notifies Matched Players
- **File:** `server/src/main/java/com/clashgame/server/service/MatchmakingService.java` (line ~137)
- **Description:** `tryMatchFromQueue()` successfully matches two players from the PvP queue but has a `// TODO: Notify both players via GameServer` comment — the matched players are never actually notified or started in a battle.
- **Impact:** The PvP queue is dead code. However, the current flow uses `findOpponent()` which searches for AI-like opponents (offline players) instead — so PvP real-time matchmaking is broken.
- **Fix:** Inject `GameServer` into `MatchmakingService`; in `tryMatchFromQueue()`, call `battleService.initiateBattle()` and send `BATTLE_START` to both clients via `GameServer.sendToPlayer()`.

### B2. [P0] Surrender Not Implemented Server-Side
- **File:** `server/src/main/java/com/clashgame/server/network/GameServer.java` (line ~275-285), `client/src/main/java/com/clashgame/client/game/GameEngine.java` (`surrender()` method)
- **Description:** The client has a `surrender()` method that sends a `LEAVE_GAME` message with the battleId as payload. The server's `LEAVE_GAME` handler checks if the payload is non-empty (implying a battle surrender), but the only action taken is `gameStateManager.endBattle(battleId)`. This abruptly ends the battle without awarding trophies/loot to the defender or penalizing the surrenderer. The `BattleEngine.endBattle()` is not called with surrender-specific logic.
- **Impact:** Surrendering appears to simply discard the battle with no consequences, creating an exploit: players can surrender when losing to avoid trophy loss.
- **Fix:** Add `surrenderBattle(battleId, surrenderingPlayerId)` to `GameStateManager`/`BattleEngine` that awards the non-surrendering player full 3-star rewards and applies appropriate penalties to the surrenderer.

### B3. [P0] `battle_deployed_units` Schema ↔ JPA Mapping Mismatch
- **Files:** `database/schema.sql` (lines 156-167), `server/src/main/java/com/clashgame/server/model/Battle.java` (lines 83-86)
- **Description:** The schema defines `battle_deployed_units` with columns `id`, `battle_id`, `unit_type`, `quantity`. However, the JPA `@ElementCollection` mapping on `Battle.deployedUnitTypes` only maps `@Column(name = "unit_type")` as a simple `List<String>`. It ignores the `id` (auto-increment PK) and `quantity` columns. JPA's `@ElementCollection` generates a table with `battle_id` + `unit_type` — it does not auto-generate an `id` PK or `quantity` column.
- **Impact:** Running `ddl-auto=none` means the manual schema is authoritative. If JPA tries to insert into this table, the missing `quantity` column (NOT NULL DEFAULT 1) may work due to the default, but the `id AUTO_INCREMENT` primary key and Hibernate's generated SQL may conflict. Also, the semantic intent (tracking quantity per type) is lost — deploying 5 Barbarians creates 5 separate rows instead of 1 row with `quantity=5`.
- **Fix:** Either: (a) Change the JPA mapping to use an `@Embeddable` class with `unitType` + `quantity` fields mapped via `@ElementCollection`, or (b) Change the schema to match JPA's expected format (drop `id` PK, drop `quantity`).

### B4. [P1] Chat Messages Not Persisted to Database
- **Files:** `server/src/main/java/com/clashgame/server/network/GameServer.java` (lines ~390-410, CHAT_MESSAGE handler), `database/schema.sql` (lines 173-190, `chat_messages` table)
- **Description:** The schema defines a `chat_messages` table with `sender_id`, `message`, `channel`, `sent_at`. However, the server's CHAT_MESSAGE handler only broadcasts the message to all connected clients — it never writes to the database. There is no `ChatMessageRepository` or `ChatService`.
- **Impact:** Chat history is volatile. Disconnecting and reconnecting loses all chat. New players joining see no conversation history.
- **Fix:** Create `ChatMessage` entity + `ChatMessageRepository` + `ChatService`. Persist each message in the CHAT_MESSAGE handler. Send recent chat history (last 50 messages) on player login.

### B5. [P1] Resource Generation Only for Online Players
- **File:** `server/src/main/java/com/clashgame/server/game/ResourceGeneratorThread.java` (lines 46-84)
- **Description:** `ResourceGeneratorThread.generateResources()` only processes players who have `isOnline() == true` and are connected via `GameServer`. Offline players receive zero resource generation.
- **Impact:** In Clash of Clans, resource collectors generate passively even when offline. This implementation punishes players for going offline — they earn nothing until they log back in.
- **Fix:** Process all players (or all players with active collectors). Calculate catch-up resources on login based on time since `lastLogin`. Or change the scheduled job to generate for all players regardless of online status.

### B6. [P1] No Heartbeat Sent by Client
- **Files:** `server/src/main/java/com/clashgame/server/network/GameServer.java` (line 228, HEARTBEAT handler), `client/src/main/java/com/clashgame/client/network/NetworkClient.java`
- **Description:** The server handles `HEARTBEAT` messages as a no-op to keep connections alive. However, the client's `NetworkClient` never sends any heartbeat messages. There is no scheduled heartbeat task.
- **Impact:** Long-idle TCP connections may be silently dropped by routers/firewalls with no detection mechanism. The server has no way to distinguish a silently-dropped client from an idle one.
- **Fix:** Add a `ScheduledExecutorService` in `NetworkClient` that sends a HEARTBEAT message every 30-60 seconds. On the server, track last heartbeat time and disconnect stale clients.

### B7. [P2] REST API Duplicates Socket Functionality But Is Unused
- **Files:** `server/src/main/java/com/clashgame/server/controller/PlayerController.java` (all endpoints), `client/src/main/java/com/clashgame/client/network/NetworkClient.java`
- **Description:** `PlayerController` exposes REST endpoints for register, login, getPlayer, leaderboard, and battleHistory. The client never uses any of these — all communication goes through the TCP socket via `GameServer.processMessage()`.
- **Impact:** Dead code that increases attack surface and maintenance burden. Spring Security is configured to `permitAll()` on all endpoints.
- **Fix:** Either remove `PlayerController` and the REST layer, or make the client use REST for non-realtime operations (login, leaderboard, training) and sockets only for real-time battle updates.

### B8. [P2] `applyBattleRewards()` in BattleEngine Is Empty
- **File:** `server/src/main/java/com/clashgame/server/game/BattleEngine.java` (lines 452-455)
- **Description:** The `applyBattleRewards()` method body is empty with a comment noting that `BattleService.saveBattleResult()` handles persistence. While rewards are indeed applied in `BattleService`, the BATTLE_RESULT JSON sent to clients via `buildBattleResultJson()` uses the `battle` entity's fields which are set correctly in `endBattle()`. This is not broken per se, but the empty method is misleading.
- **Impact:** Minor — the flow works because `BattleService.saveBattleResult()` reloads fresh entities. But in-memory `Player` objects in `BattleEngine` do not reflect the rewards, which could cause issues if they're referenced after battle.
- **Fix:** Either populate the method with in-memory updates for correctness, or remove it entirely and document clearly that persistence happens in `BattleService`.

### B9. [P2] Defender Is Not Notified of Incoming Attack — No Defense View
- **Files:** `server/src/main/java/com/clashgame/server/service/BattleService.java` (lines ~55-95), `server/src/main/java/com/clashgame/server/game/BattleEngine.java` (sends updates to both attacker and defender)
- **Description:** While `BattleEngine` sends `BATTLE_UPDATE` and `BATTLE_RESULT` to both attacker and defender clients, the defender has no battle viewing UI. The client's `GameEngine.handleBattleStart()` only sets up the battle view for the attacker (the one who initiated via "Attack Player"). If a defender is online, they receive battle messages but the client has no handler to display a defense replay view.
- **Impact:** Online defenders see no indication they're being attacked. They may receive a BATTLE_RESULT alert but miss the entire attack.
- **Fix:** Add a "Your base is under attack!" notification for online defenders. Optionally provide a spectator/defense view that shows the battle in real-time (read-only, no deployment).

---

## C. Data Mismatches (Client ↔ Server ↔ DB)

### C1. [P1] Building HP Field Name Confusion Between Client and Server
- **Server:** `server/src/main/java/com/clashgame/server/model/Building.java` — `hitPoints` (@Column "hit_points") = max HP, `currentHitPoints` (@Column "max_hit_points") — this is semantically inverted. The field named `currentHitPoints` maps to the column `max_hit_points`.
- **Client:** `client/src/main/java/com/clashgame/client/model/Building.java` — `hitPoints` = max HP (set in `initializeFromType()`), `currentHitPoints` = current HP (set equal to hitPoints).
- **Schema:** `database/schema.sql` — columns `hit_points` and `max_hit_points` on the `buildings` table.
- **Description:** The server's `Building.java` has `@Column(name = "max_hit_points") private int currentHitPoints;` and `@Column(name = "hit_points") private int hitPoints;`. This means the field `currentHitPoints` is stored in the `max_hit_points` DB column, and the field `hitPoints` is stored in the `hit_points` DB column. If the schema's intent is `hit_points` = current and `max_hit_points` = max, then the mapping is swapped.
- **Impact:** Could cause incorrect HP display and battle calculations using wrong values.
- **Fix:** Verify the intended semantics and swap either the `@Column` annotations or the field names to be consistent. Recommend: `hitPoints` → `@Column("max_hit_points")` (max), `currentHitPoints` → `@Column("hit_points")` (current).

### C2. [P1] Client Unit Stats Hardcoded Independently from Server
- **Files:** `client/src/main/java/com/clashgame/client/model/Unit.java` (`initializeFromType`), `server/src/main/java/com/clashgame/server/model/UnitType.java`
- **Description:** The client's `Unit` model hardcodes base stats (HP, ATK, SPD, housing) in `initializeFromType()`. The server's `UnitType` enum also hardcodes these values. The two sets must be manually kept in sync. Currently they appear aligned, but any server-side balance change will desync.
- **Impact:** Client-side validation (e.g., army capacity in `TrainController`) may disagree with server if stats diverge. Battle display may show incorrect HP bars.
- **Fix:** Have the server send unit stats as part of the `LOGIN_SUCCESS` or `UNIT_TRAINED` messages. Client should parse stats from server data rather than hardcoding them.

### C3. [P1] Client Building Stats Hardcoded Independently from Server
- **Files:** `client/src/main/java/com/clashgame/client/model/Building.java` (`initializeFromType`), `server/src/main/java/com/clashgame/server/model/BuildingType.java`
- **Description:** Same issue as C2 but for buildings. Client hardcodes building dimensions, HP, defense values, and production rates. Server has its own values in `BuildingType` enum. Must be kept in sync manually.
- **Fix:** Server should send building stat definitions on login; client should use server-provided data.

### C4. [P2] Client Building Cost Map Duplicated from Server
- **Files:** `client/src/main/java/com/clashgame/client/controller/BaseController.java` (lines 72-79, BUILDING_COSTS map), `server/src/main/java/com/clashgame/server/model/BuildingType.java` (goldCost per type)
- **Description:** Building gold costs are hardcoded in both the client's `BaseController.initialize()` (local map) and the server's `BuildingType` enum. Any change to one must be mirrored to the other.
- **Fix:** Remove client-side cost map; request costs from server or derive from server-sent building definitions.

### C5. [P2] Client Barracks Level Gating Duplicated from Server
- **Files:** `client/src/main/java/com/clashgame/client/controller/TrainController.java` (barracks level checks), `server/src/main/java/com/clashgame/server/service/PlayerService.java` (barracks level checks in `trainUnit()`)
- **Description:** Both client and server independently maintain the logic for which barracks level unlocks which unit types (Lv1: Barbarian/Archer/Goblin, Lv2: Giant/Wizard, etc.). Duplication creates desync risk.
- **Fix:** Server should provide unlocked unit types as part of player data. Client should display based on server-provided unlock info.

### C6. [P2] `GameMessage` Duplicated Between Server and Client
- **Files:** `server/src/main/java/com/clashgame/server/model/GameMessage.java`, `client/src/main/java/com/clashgame/server/model/GameMessage.java`
- **Description:** The `GameMessage` class exists identically in both server and client source trees under the same package `com.clashgame.server.model`. Both have `serialVersionUID = 1L`. Any edit to one must be mirrored exactly in the other, or `ObjectInputStream` deserialization will fail with `InvalidClassException`.
- **Fix:** Extract `GameMessage` into a shared `common` module that both client and server depend on via Maven.

### C7. [P3] Schema Sample Data May Conflict with Application State
- **File:** `database/schema.sql` (lines 215-306, INSERT statements)
- **Description:** The schema contains sample data (5 players with buildings, units, battles). Running the schema on a fresh database pre-populates data, but these players don't have proper BCrypt-hashed passwords — they use plaintext like `$2a$10$...` (which are BCrypt-formatted but may not match expected passwords).
- **Impact:** Minor — sample data is for development/testing only; may confuse production setup.
- **Fix:** Document that sample INSERTs are for development only, or create a separate `seed.sql` file.

---

## D. UI/UX Gaps

### D1. [P1] No Resource Update Animation or Real-Time Polling from Dashboard
- **Files:** `client/src/main/java/com/clashgame/client/controller/DashboardController.java`
- **Description:** The dashboard binds labels to player properties, but resources only update when the client receives a `RESOURCE_UPDATE` message. The `ResourceGeneratorThread` only sends updates every 60 seconds, and only to online players. There is no client-side polling or periodic refresh. After spending resources (upgrading/training), the dashboard does not immediately refresh — it relies on the next server push.
- **Impact:** Resource counts feel static and unresponsive.
- **Fix:** Request a `BASE_UPDATE` on every screen navigation. Add an animation timer for smooth resource counter updates.

### D2. [P1] No Loading State for Attack/Matchmaking
- **File:** `client/src/main/java/com/clashgame/client/controller/DashboardController.java` (lines ~100-130, `onAttackClicked`)
- **Description:** When clicking "Attack Player", the code calls `findOpponent()` and then starts a 5-second `Thread.sleep` loop waiting for a `MATCH_FOUND` message. This blocks the calling thread (though it's on a background thread). There is no visual loading indicator — the button is just disabled. If no match is found in 5 seconds, a generic "No opponents found" message appears.
- **Impact:** Poor UX — player sees a frozen button with no feedback.
- **Fix:** Add a "Searching for opponent..." overlay with a ProgressIndicator and cancel button.

### D3. [P2] No Error Feedback for Network Disconnections
- **File:** `client/src/main/java/com/clashgame/client/network/NetworkClient.java`
- **Description:** When the server connection drops, the `NetworkClient.listenForMessages()` loop catches the exception, logs it, and calls `disconnect()`. No UI notification is shown to the player. They remain on whatever screen they were on with a dead connection.
- **Impact:** Player may continue clicking buttons with no response, thinking the game is frozen.
- **Fix:** Add a `connectionLost` callback that triggers a `Platform.runLater()` alert dialog offering to reconnect or return to login.

### D4. [P2] Chat Panel Embedded Only in Dashboard
- **Files:** `client/src/main/resources/fxml/dashboard_screen.fxml` (`<fx:include source="chat_panel.fxml"/>`), all other FXML files
- **Description:** The chat panel is only `<fx:include>`-ed in the dashboard screen. It does not appear on the base, train, or leaderboard screens. During battle, there is no chat.
- **Impact:** Players can only chat from the dashboard. Navigating to any other screen removes chat access.
- **Fix:** Create a persistent chat overlay or include the chat panel in the application root layout so it's accessible from all screens.

### D5. [P2] No Confirmation for Troop Training
- **File:** `client/src/main/java/com/clashgame/client/controller/TrainController.java`
- **Description:** Clicking "Train" immediately sends the train request to the server. There is no confirmation dialog showing the cost. Mis-clicks can waste elixir.
- **Fix:** Add a confirmation dialog: "Train 5 Barbarians for 250 Elixir?"

### D6. [P2] Base Canvas Not Responsive / Fixed Size
- **File:** `client/src/main/resources/fxml/base_screen.fxml` (`<Canvas fx:id="baseCanvas" width="600" height="600"/>`)
- **Description:** The base canvas is hardcoded at 600×600 pixels. It does not resize with the window. On high-DPI displays, it may appear small; on low-res displays, it may be too large.
- **Fix:** Bind canvas width/height to parent container dimensions and recalculate `cellWidth`/`cellHeight` on resize.

### D7. [P3] Battle Canvas Fixed at 700×500
- **File:** `client/src/main/resources/fxml/battle_screen.fxml` (`<Canvas fx:id="battleCanvas" width="700" height="500"/>`)
- **Description:** Same fixed-size issue as D6 for the battle canvas.
- **Fix:** Same approach — bind to parent and scale.

### D8. [P3] No Tooltip or Help System
- **Description:** While dashboard buttons have tooltips (via FXML), base building types in the ComboBox have no tooltips showing stats/costs. Unit types in the train ComboBox show info only after selection. No in-game tutorial or help screen exists.
- **Fix:** Add tooltips or a help panel. Consider a first-login tutorial overlay.

---

## E. Missing OOP / Design Patterns

### E1. [P1] No Interface Abstraction for Game Entities
- **Description:** There are no interfaces such as `Attackable`, `Damageable`, `Producible`, or `Upgradeable` that building and unit types could implement. All type-specific behavior is encoded in `switch` statements over string type names throughout the codebase.
- **Examples:**
  - `server/src/main/java/com/clashgame/server/game/BattleEngine.java`: Multiple switch blocks over unit/building type strings for targeting, damage, AI behavior (lines ~180-360)
  - `client/src/main/java/com/clashgame/client/controller/BaseController.java`: Giant switch in `drawBuilding()` for rendering each type (lines ~200-960)
  - `client/src/main/java/com/clashgame/client/controller/BattleController.java`: Duplicate switch blocks for battle rendering (lines ~250-900)
  - `client/src/main/java/com/clashgame/client/controller/TrainController.java`: Switch for unit cost/stats (lines ~70-130)
  - `client/src/main/java/com/clashgame/client/model/Building.java`: Switch in `initializeFromType()` (lines ~42-100)
  - `client/src/main/java/com/clashgame/client/model/Unit.java`: Switch in `initializeFromType()` (lines ~37-75)
- **Impact:** Adding a new building or unit type requires modifications in 10+ files across both client and server. Violates Open/Closed Principle.
- **Fix:** Define interfaces: `interface Renderable { void render(GraphicsContext gc, ...) }`, `interface Combatable { int getDPS(); double getRange(); }`. Use polymorphism or a registry pattern (`Map<String, BuildingRenderer>`) instead of switch statements.

### E2. [P1] God Class: `GameEngine.java` (737 lines)
- **File:** `client/src/main/java/com/clashgame/client/game/GameEngine.java`
- **Description:** `GameEngine` is a singleton that handles: player state management, battle state management, message dispatching for 15+ message types, JSON parsing, army serialization, screen navigation, and acts as an observable data store. It has 25+ observable properties and 15+ handler methods.
- **Impact:** Extremely difficult to test, maintain, or extend. Single Responsibility Principle violation.
- **Fix:** Split into: `PlayerStateManager`, `BattleStateManager`, `MessageRouter`, `ScreenNavigator`. Use an event bus pattern for decoupling.

### E3. [P1] God Class: `BaseController.java` (1120 lines)
- **File:** `client/src/main/java/com/clashgame/client/controller/BaseController.java`
- **Description:** Contains: building rendering (12 detailed drawing methods), grid management, building selection, placement mode, move mode, upgrade logic, remove logic, resource display, and canvas event handling — all in one class.
- **Fix:** Extract `BuildingRenderer` (or per-type renderers), `GridManager`, `BuildingActionHandler` classes. Controller should only wire them together.

### E4. [P1] God Class: `BattleController.java` (1238 lines)
- **File:** `client/src/main/java/com/clashgame/client/controller/BattleController.java`
- **Description:** Contains: AnimationTimer management, building rendering (12 types duplicated from BaseController), unit rendering, projectile rendering, HUD updates, deployment logic, surrender, unit selection, and cleanup.
- **Fix:** Extract `BattleBuildingRenderer`, `UnitRenderer`, `ProjectileRenderer`, `BattleHUD` classes.

### E5. [P2] Massive Code Duplication: Building Renderers
- **Files:** `client/src/main/java/com/clashgame/client/controller/BaseController.java` (lines ~200-960), `client/src/main/java/com/clashgame/client/controller/BattleController.java` (lines ~250-900)
- **Description:** All 12 building type rendering methods are fully duplicated between `BaseController` and `BattleController`. The battle versions add a `brightness` parameter but are otherwise identical. This is ~700 lines of duplicated rendering code.
- **Impact:** Any visual change to a building must be made in two places. Bug fixes must be duplicated.
- **Fix:** Create a `BuildingRendererFactory` or shared utility class with parameterized rendering methods that both controllers use.

### E6. [P2] No Observer/Event Bus Pattern
- **Description:** The client uses JavaFX property binding for some data flow, but much of the communication between components is through direct method calls and listener chaining. There's no formal event bus for game events.
- **Fix:** Implement a lightweight event bus (`EventBus.publish(Event)` / `EventBus.subscribe(EventType, Handler)`) for decoupled communication between game components.

### E7. [P2] No Factory Pattern for Entity Creation
- **Description:** Building and unit creation is done through constructors with string types, followed by `switch`-based initialization. No factory classes exist.
- **Fix:** Create `BuildingFactory.create(BuildingType)` and `UnitFactory.create(UnitType)` that encapsulate initialization logic.

### E8. [P2] No Strategy Pattern for Unit AI
- **File:** `server/src/main/java/com/clashgame/server/game/BattleEngine.java` (lines ~180-360)
- **Description:** Unit targeting and movement AI is implemented via a large `switch` block in `BattleEngine` based on unit type strings. Goblins target resources, Giants target defenses, Healers target injured allies, etc. — all encoded imperatively.
- **Fix:** Define `interface TargetingStrategy { Building selectTarget(Unit unit, List<Building> buildings) }` with implementations per unit type. Compose strategies onto unit instances.

### E9. [P3] No Command Pattern for Player Actions
- **Description:** Player actions (place, move, remove, upgrade buildings; train units; deploy units) are handled directly in controllers and services. There's no undoable command pattern.
- **Fix:** Implement `Command` objects for each action to enable undo/redo, action queuing, and replay.

### E10. [P3] No DTO Layer Between Server and Client
- **Description:** The server serializes JPA entities directly to JSON via manual `StringBuilder` (e.g., `PlayerService.getPlayerDataJson()`). There are no Data Transfer Objects. Changes to entities risk leaking internal fields or breaking client parsing.
- **Fix:** Create DTOs: `PlayerDTO`, `BuildingDTO`, `BattleResultDTO`. Map entities to DTOs before serialization.

---

## F. Missing Server Features

### F1. [P0] No Input Validation / Sanitization on Chat Messages
- **File:** `server/src/main/java/com/clashgame/server/network/GameServer.java` (lines ~390-410)
- **Description:** Chat messages are broadcast as-is. There is no profanity filter, no message length limit enforcement (client limits to 200 chars but server doesn't), no HTML/script escaping, and no rate limiting. A malicious client can send unlimited messages of any content.
- **Impact:** Potential for chat spam, abuse, and injection if the client renders messages as rich text.
- **Fix:** Add server-side length validation (max 200 chars), rate limiting (e.g., 1 message per second per player), and basic content filtering.

### F2. [P0] No Authentication Token / Session Management
- **Files:** `server/src/main/java/com/clashgame/server/network/GameServer.java`, `server/src/main/java/com/clashgame/server/network/ClientHandler.java`
- **Description:** After login, the server tracks the player by username string stored in `playerConnections`. There is no session token, no session expiry, and no re-authentication. Any client that knows a player's username could potentially impersonate them by sending messages with that `senderId`.
- **Impact:** Significant security vulnerability. Message sender identity is based on trust.
- **Fix:** Generate a session UUID on successful login; require it in all subsequent messages; validate server-side.

### F3. [P1] Spring Security Configured to Permit All
- **File:** `server/src/main/java/com/clashgame/server/config/GameConfig.java` (lines ~14-32)
- **Description:** The `SecurityFilterChain` is configured with `authorizeHttpRequests(auth -> auth.anyRequest().permitAll())` and CSRF disabled. All REST endpoints are completely unprotected.
- **Impact:** If the REST API is kept (see B7), anyone can call register/login/getPlayer/leaderboard without authentication. Combined with F2, there's no security layer.
- **Fix:** Either remove the REST endpoints, or implement proper JWT-based authentication.

### F4. [P1] No Rate Limiting on Any Operation
- **Description:** There is no rate limiting on login attempts, registration, building operations, chat messages, matchmaking requests, or any other operation. A malicious client can flood the server.
- **Impact:** DDoS potential, resource exhaustion, data pollution.
- **Fix:** Implement per-player rate limiting using a token bucket algorithm or a simple counter with time windows.

### F5. [P1] No Graceful Server Shutdown
- **Files:** `server/src/main/java/com/clashgame/server/network/GameServer.java`, `server/src/main/java/com/clashgame/server/game/GameStateManager.java`
- **Description:** There is no `@PreDestroy` or shutdown hook that: saves active battle states, notifies connected clients of impending shutdown, closes client connections gracefully, or stops the game tick loop cleanly. An abrupt server stop loses all in-progress battles.
- **Fix:** Add `@PreDestroy` methods to `GameServer` and `GameStateManager` that: broadcast a shutdown message, end active battles as draws, close all ClientHandler connections, and stop ExecutorService threads.

### F6. [P1] No Validation of Deployment Position in Battle
- **File:** `server/src/main/java/com/clashgame/server/service/BattleService.java` (lines ~105-145, `deployUnit`)
- **Description:** The `deployUnit()` method parses the unit type and coordinates from JSON but does not validate that the deployment position is at the edge of the map. Only the client enforces the `isEdgePosition()` check.
- **Impact:** A modified client can deploy units anywhere on the map, including directly on top of the Town Hall.
- **Fix:** Add server-side edge position validation in `BattleService.deployUnit()`.

### F7. [P2] No Concurrent Battle Protection
- **Files:** `server/src/main/java/com/clashgame/server/service/BattleService.java`, `server/src/main/java/com/clashgame/server/network/GameServer.java`
- **Description:** There is no check preventing a player from attacking while already in an active battle. A player could potentially start multiple simultaneous battles.
- **Fix:** Check `gameStateManager.getActiveBattles()` for the player before initiating a new battle.

### F8. [P2] No Player Disconnect Handling During Battle
- **File:** `server/src/main/java/com/clashgame/server/network/ClientHandler.java` (disconnect handling)
- **Description:** When a client disconnects, `ClientHandler` calls `GameServer.handlePlayerDisconnect()` which sets the player offline. However, if the disconnecting player is in an active battle, the battle continues running with no connected client to receive updates. The battle eventually ends by timeout, but results may not be properly communicated.
- **Fix:** On disconnect, check if the player has an active battle and either pause it, end it as a surrender, or set a reconnection grace period.

### F9. [P2] XP and Level System Only Partially Implemented
- **File:** `server/src/main/java/com/clashgame/server/service/BattleService.java` (lines ~170-175, `saveBattleResult`)
- **Description:** XP is awarded after battles (stars × 10) in `BattleService.saveBattleResult()`. The `Player.addExperience()` method handles level-up (100 XP per level). However, XP is only earned from battles — no XP from building, upgrading, training, or any other action.
- **Impact:** Players who don't battle much will remain at level 1 indefinitely.
- **Fix:** Award XP for all meaningful actions: placing buildings, upgrading, training units, first login of the day, etc.

### F10. [P3] Manual JSON Serialization Throughout
- **Files:** `server/src/main/java/com/clashgame/server/service/PlayerService.java` (`getPlayerDataJson`, `getLeaderboard`), `server/src/main/java/com/clashgame/server/game/BattleEngine.java` (`buildBattleResultJson`, `sendBattleUpdate`), `server/src/main/java/com/clashgame/server/network/GameServer.java` (multiple handlers)
- **Description:** All JSON is built manually via `StringBuilder` and `String.format()`. Jackson is available in the dependencies but not used for the socket protocol. This is error-prone (no escaping of special characters in strings like usernames) and verbose.
- **Impact:** A username containing `"` or `\` will produce invalid JSON. Difficult to maintain as payloads grow.
- **Fix:** Use Jackson `ObjectMapper` for all JSON serialization. Create proper POJO/DTO classes that Jackson can serialize automatically.

---

## G. Code Quality Issues

### G1. [P1] No Unit Tests
- **Description:** Neither the server nor the client project contains any test files. Both pom.xml files include JUnit 5 as a test dependency, but no test classes exist.
- **Impact:** Zero automated test coverage. Any refactoring or feature addition risks silent regressions.
- **Fix:** Start with critical path tests: `PlayerServiceTest` (registration, authentication, upgrade validation), `BattleEngineTest` (combat calculations, star counting, loot calculation), `MatchmakingServiceTest` (trophy range, cooldown).

### G2. [P1] Exception Handling: Catch-All Swallowing
- **Files:** `server/src/main/java/com/clashgame/server/network/GameServer.java` (every `case` in `processMessage` wrapped in try-catch), `client/src/main/java/com/clashgame/client/game/GameEngine.java` (every handler wrapped in try-catch)
- **Description:** Nearly every operation is wrapped in `try { ... } catch (Exception e) { e.printStackTrace(); }` or `logger.error(...)`. Exceptions are caught, logged, and silently consumed. No distinction between recoverable and fatal errors. The client often receives no indication of what went wrong.
- **Fix:** Create specific exception types (`GameException`, `ValidationException`, `BattleException`). Propagate actionable errors to the client via ERROR messages. Only catch exceptions you can meaningfully handle.

### G3. [P1] Thread Safety Concerns in GameEngine
- **File:** `client/src/main/java/com/clashgame/client/game/GameEngine.java`
- **Description:** `GameEngine` is a singleton accessed from both the JavaFX Application Thread (UI) and the `NetworkClient` listener thread (background). While many fields use JavaFX properties (which have some thread-safety guarantees with `Platform.runLater()`), the singleton's state is mutated from multiple threads. The `handleBaseUpdate()` method performs extensive JSON parsing and object creation that could race with UI reads.
- **Fix:** Ensure all state mutations happen on the JavaFX Application Thread via `Platform.runLater()`. Add synchronization where needed for shared mutable state.

### G4. [P2] Hardcoded Server Connection Details
- **File:** `client/src/main/java/com/clashgame/client/network/NetworkClient.java`
- **Description:** Server host (`localhost`) and port (`9090`) are hardcoded in the `NetworkClient` constructor. There's no configuration file, command-line argument, or settings dialog.
- **Fix:** Externalize to a `client.properties` file or add a server address field on the login screen.

### G5. [P2] No Logging Framework on Client
- **File:** `client/src/main/java/com/clashgame/client/network/NetworkClient.java`
- **Description:** The client uses `System.out.println()` and `e.printStackTrace()` for logging despite having SLF4J in the POM. None of the client classes use the SLF4J logger.
- **Fix:** Replace all `System.out.println()` and `e.printStackTrace()` with `Logger` from SLF4J.

### G6. [P2] Magic Numbers Throughout
- **Examples:**
  - `server/src/main/java/com/clashgame/server/game/BattleEngine.java`: `20` ticks/sec, `7.5` tile range, `3.0` tile range, `4` updates/sec, `5` heal amount, etc.
  - `server/src/main/java/com/clashgame/server/service/MatchmakingService.java`: `200` trophy range, `5` minute cooldown, `100` max queue
  - `server/src/main/java/com/clashgame/server/service/PlayerService.java`: `100` XP per level, building max counts (3, 4, 2, etc.)
  - `client/src/main/java/com/clashgame/client/controller/BattleController.java`: `PROJECTILE_LIFETIME = 0.6`, `0.25` update interval
  - `client/src/main/java/com/clashgame/client/controller/TrainController.java`: Unit cost maps hardcoded inline
- **Fix:** Extract all magic numbers into named constants in a `GameConstants` class or configuration file.

### G7. [P2] Resource Leak: ExecutorService Not Shut Down
- **File:** `server/src/main/java/com/clashgame/server/game/GameStateManager.java`
- **Description:** `GameStateManager` creates a `FixedThreadPool` `ExecutorService` for running battles but has no `@PreDestroy` or shutdown method. Threads may leak on application shutdown.
- **Fix:** Add `@PreDestroy public void shutdown() { executorService.shutdownNow(); }`.

### G8. [P2] JSON Parsing with Manual String Extraction — Fragile
- **Files:** `client/src/main/java/com/clashgame/client/game/GameEngine.java` (`handleBaseUpdate`, `handleBattleStart`, `handleBattleUpdate`), `server/src/main/java/com/clashgame/server/service/BattleService.java` (`deployUnit`)
- **Description:** JSON parsing is done via Jackson `ObjectMapper.readTree()` in some places and manual `String.contains()`/`String.substring()` in others. The server-side `PlayerService.getPlayerDataJson()` builds JSON with `StringBuilder`, while the client parses it with Jackson. This mixed approach is error-prone.
- **Fix:** Standardize on Jackson `ObjectMapper` for both serialization and deserialization across the entire codebase.

### G9. [P3] Module System Usage (JPMS) Without Full Benefit
- **File:** `client/src/main/java/module-info.java`
- **Description:** The client uses Java 9+ module system (`module com.clashgame.client`). This is good, but it exports `com.clashgame.server.model` — a server package embedded in the client for `GameMessage`. This cross-package export is a code smell.
- **Fix:** Extract shared types into a `com.clashgame.common` module.

### G10. [P3] PostgreSQL Dependency Included But Not Used
- **File:** `server/pom.xml`
- **Description:** The server POM includes both `mysql-connector-j` and `postgresql` driver dependencies. `application.properties` configures MySQL. PostgreSQL is unused.
- **Fix:** Remove the PostgreSQL dependency to reduce artifact size and avoid confusion.

### G11. [P3] No JavaDoc on Public APIs
- **Description:** While most classes have a top-level class comment, many public methods lack JavaDoc. Service layer methods (`PlayerService`, `BattleService`, `MatchmakingService`) have sparse or no JavaDoc explaining parameters, return values, and exceptions.
- **Fix:** Add JavaDoc to all public methods in service and model classes.

---

## Summary Table

| Category | P0 (Critical) | P1 (Major) | P2 (Moderate) | P3 (Minor) | Total |
|----------|:-:|:-:|:-:|:-:|:-:|
| A. Missing Game Features | 2 | 3 | 2 | 2 | 9 |
| B. Broken/Incomplete Flows | 3 | 3 | 3 | 0 | 9 |
| C. Data Mismatches | 0 | 3 | 3 | 1 | 7 |
| D. UI/UX Gaps | 0 | 2 | 4 | 2 | 8 |
| E. Missing OOP Patterns | 0 | 4 | 4 | 2 | 10 |
| F. Missing Server Features | 2 | 5 | 3 | 1 | 11 |
| G. Code Quality Issues | 0 | 3 | 5 | 3 | 11 |
| **Total** | **7** | **23** | **24** | **11** | **65** |

---

## Recommended Priority Order

### Immediate (P0 — Fix before any demo/release)
1. **B3** — Fix `battle_deployed_units` schema ↔ JPA mismatch
2. **B1** — Implement PvP matchmaking queue notification
3. **B2** — Implement proper surrender with consequences
4. **A1** — Implement building upgrade timers
5. **A2** — Implement unit training timers
6. **F1** — Add chat input validation/sanitization
7. **F2** — Implement session-based authentication

### Short-term (P1 — Core experience gaps)
1. **C1** — Fix Building HP column mapping confusion
2. **C6** — Extract shared `GameMessage` into common module
3. **B4** — Persist chat messages to database
4. **B5** — Enable offline resource generation
5. **E1** — Introduce interfaces to replace type-string switches
6. **E2/E3/E4** — Refactor god classes (GameEngine, BaseController, BattleController)
7. **G1** — Write unit tests for critical services
8. **A3** — Implement gem currency spending
9. **A4** — Implement or remove clan system
10. **A5** — Implement laboratory functionality

### Medium-term (P2 — Polish and robustness)
1. **E5** — Deduplicate building renderers
2. **F6** — Server-side deployment position validation
3. **D1** — Resource update animation
4. **G6** — Extract magic numbers to constants
5. **F10** — Switch to Jackson for all JSON serialization

---

*End of Audit Report v2*
