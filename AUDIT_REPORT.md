# Comprehensive Project Audit Report
## Online Strategy Game (Clash of Clans Style Mini)

---

## 1. BUGS (Critical Issues)

### BUG-01: TrainController displays wildly incorrect unit stats
- **File:** `client/src/main/java/com/clashgame/client/controller/TrainController.java` lines 40–48
- **Problem:** The `UNIT_HP` and `UNIT_ATK` maps show inflated/wrong values that don't match the actual server `UnitType` enum or the client `Unit.initializeFromType()`.

  | Unit      | Displayed HP | Actual HP | Displayed ATK | Actual ATK |
  |-----------|-------------|-----------|---------------|------------|
  | BARBARIAN | 150         | 50        | 20            | 20         |
  | ARCHER    | 80          | 30        | 15            | 15         |
  | GIANT     | 400         | 200       | 30            | **35**     |
  | GOBLIN    | 60          | 25        | 12            | **10**     |
  | WIZARD    | 100         | 60        | 50            | 50         |
  | DRAGON    | 800         | 300       | 60            | **80**     |
  | HEALER    | 500         | 80        | 0             | 0          |

- **Impact:** Players see 2–10× inflated HP in training tooltips. Giant/Goblin/Dragon ATK values also differ.
- **Fix:** Replace `UNIT_HP` and `UNIT_ATK` map values to match `server/model/UnitType.java`:
  ```java
  private static final Map<String, Integer> UNIT_HP = Map.of(
      "BARBARIAN", 50, "ARCHER", 30, "GIANT", 200,
      "GOBLIN", 25, "WIZARD", 60, "DRAGON", 300, "HEALER", 80
  );
  private static final Map<String, Integer> UNIT_ATK = Map.of(
      "BARBARIAN", 20, "ARCHER", 15, "GIANT", 35,
      "GOBLIN", 10, "WIZARD", 50, "DRAGON", 80, "HEALER", 0
  );
  ```

---

### BUG-02: Client doesn't parse `storageCapacity` from server data
- **File:** `client/src/main/java/com/clashgame/client/game/GameEngine.java`, `handleBaseUpdate()` ~lines 340–356
- **Problem:** The server sends `storageCapacity` for each building in `getPlayerDataJson()`, but the client's building parsing in `handleBaseUpdate()` never reads it. Client buildings use hardcoded base-level `storageCapacity` from `initializeFromType()`.
- **Impact:** After upgrading an Army Camp (which multiplies storageCapacity by 1.25×/level), the client still shows the base capacity. The army capacity calculation in `DashboardController` uses this stale value, so players see incorrect army capacity limits.
- **Fix:** Add to `handleBaseUpdate()` building parsing:
  ```java
  if (bNode.has("storageCapacity")) building.setStorageCapacity(bNode.get("storageCapacity").asInt());
  ```

---

### BUG-03: Trophy calculation formula is inverted for defender
- **File:** `server/src/main/java/com/clashgame/server/model/Battle.java`, `calculateTrophyExchange()` ~lines 148–157
- **Problem:** The formula calculates:
  ```java
  trophiesWon = starsEarned * baseTrophies / 3;    // attacker reward
  trophiesLost = baseTrophies - trophiesWon;        // defender penalty
  ```
  Results: 1 star → attacker +10, defender −20. 3 stars → attacker +30, defender −0.
  **The defender loses MORE trophies when the attack is weaker** — this is backwards.
- **Fix:** The defender's loss should scale WITH the attacker's success:
  ```java
  trophiesLost = starsEarned * baseTrophies / 3;    // defender loses more as attack succeeds more
  trophiesWon = baseTrophies - trophiesLost;         // or keep trophiesWon as-is and fix trophiesLost
  ```
  Or simply: `trophiesLost = trophiesWon;` (symmetric exchange).

---

### BUG-04: `productionRate` truncated from double to int on client
- **File:** `client/src/main/java/com/clashgame/client/game/GameEngine.java`, `handleBaseUpdate()` ~line 353
- **Problem:** `building.setProductionRate((int) bNode.get("productionRate").asDouble())` — server sends `"productionRate": 2.5`, client stores `2`. The client `Building.productionRate` is `IntegerProperty` while the server's is `double`.
- **Impact:** Production rate display and calculations lose precision on client side.
- **Fix:** Either change client `Building.productionRate` to `DoubleProperty`, or use `Math.round()` instead of cast.

---

### BUG-05: `sendBattleUpdate()` uses fragile JSON mutation
- **File:** `server/src/main/java/com/clashgame/server/game/BattleEngine.java`, `sendBattleUpdate()` ~line 525
- **Problem:** The method builds JSON with a closing `}`, then uses `sb.deleteCharAt(sb.length() - 1)` to remove it, and appends a `,\"fireEvents\":[...]}}`. Any upstream format change (e.g., adding a trailing field) would produce malformed JSON.
- **Fix:** Build the full JSON structure up front, including `fireEvents` within the initial `StringBuilder` construction before adding the closing `}`.

---

### BUG-06: BaseController imports server-side model class
- **File:** `client/src/main/java/com/clashgame/client/controller/BaseController.java`
- **Problem:** Imports `com.clashgame.server.model.GameMessage`, creating a direct dependency from the client module to the server module. This breaks module separation for a client-server architecture.
- **Fix:** Use the client's own reference to `GameMessage` (already accessible via `module-info.java`), or move `GameMessage` to a shared `common` module.

---

### BUG-07: No cleanup on screen navigation — memory/resource leak
- **File:** `client/src/main/java/com/clashgame/client/GameClient.java` (screen nav), `BattleController.java`, all controllers
- **Problem:** `GameClient` loads a fresh FXML + controller every navigation. Old controllers are never disposed:
  - `BattleController`'s `AnimationTimer` keeps running in the background
  - Property listeners (elapsed, destruction, stars, etc.) in `BattleController` are never removed
  - Each navigation accumulates undisposed objects
- **Impact:** Gradual memory leak; stale AnimationTimers consume CPU; stale listeners can cause `IllegalStateException` on the wrong JavaFX thread.
- **Fix:** Add a `dispose()` method to each controller (especially `BattleController`) and call it before loading the next screen. At minimum, stop the `AnimationTimer` and remove all property listeners.

---

### BUG-08: DashboardController attack button spawns unmanaged thread
- **File:** `client/src/main/java/com/clashgame/client/controller/DashboardController.java`, `onAttackClicked()`
- **Problem:** Spawns `new Thread(() -> { Thread.sleep(5000); ... })` for the 5-second cooldown re-enable. The thread has no reference, no daemon flag, no cancellation mechanism. Multiple rapid clicks or navigating away spawns orphan threads.
- **Fix:** Replace with `javafx.animation.PauseTransition`:
  ```java
  PauseTransition pause = new PauseTransition(Duration.seconds(5));
  pause.setOnFinished(e -> attackButton.setDisable(false));
  pause.play();
  ```

---

### BUG-09: `GameEngine.updateBattle()` is empty — no animation interpolation
- **File:** `client/src/main/java/com/clashgame/client/game/GameEngine.java`, lines 188–191
- **Problem:** The method body is only comments. The game loop calls `updateBattle(deltaTime)` every frame during battle, but it does nothing. This means units teleport between positions on each server tick (~50ms) rather than smoothly moving.
- **Impact:** Visually jarring battle animations; units appear to jump across the screen.
- **Fix:** Implement position interpolation using the last two server update positions and lerp between them based on `deltaTime`.

---

### BUG-10: NetworkClient receiver thread dies silently on disconnect
- **File:** `client/src/main/java/com/clashgame/client/network/NetworkClient.java`, receive loop
- **Problem:** If the server disconnects or the socket dies, the receive thread catches the exception, prints to stderr, and exits. The user sees a frozen UI with no indication of lost connection.
- **Fix:** Add a disconnect callback / `onDisconnect` listener that triggers a UI notification (e.g., "Connection lost — please restart") and navigates to login screen.

---

### BUG-11: Race condition in `trainUnit()` — army capacity can be exceeded
- **File:** `server/src/main/java/com/clashgame/server/service/PlayerService.java`, `trainUnit()` ~line 340
- **Problem:** The sequence: check capacity → deduct elixir → add units is NOT atomic. If two concurrent training requests arrive, both can pass the capacity check before either adds units, resulting in overcapacity.
- **Fix:** Add `synchronized` block on the player object, or use `@Transactional(isolation = Isolation.SERIALIZABLE)`, or use database-level locking (`SELECT ... FOR UPDATE`).

---

### BUG-12: Race condition in `placeBuilding()` — building limits can be bypassed
- **File:** `server/src/main/java/com/clashgame/server/service/PlayerService.java`, `placeBuilding()`
- **Problem:** Same as BUG-11 — building count check + gold deduction + placement are not atomic.
- **Fix:** Same approaches as BUG-11.

---

### BUG-13: `moveBuilding` doesn't update client-side occupancy grid
- **File:** `client/src/main/java/com/clashgame/client/model/Base.java`
- **Problem:** When a building move is confirmed, the client Base's `boolean[][] occupancyGrid` isn't rebuilt. Future `canPlaceBuilding()` checks see stale data until a full base reload.
- **Fix:** After a successful move, rebuild the occupancy grid (clear all cells, re-mark all buildings).

---

### BUG-14: Chat messages not persisted to database
- **File:** `server/src/main/java/com/clashgame/server/network/GameServer.java`, `handleChatMessage()`
- **Problem:** The `chat_messages` table exists in `schema.sql`, but the server only broadcasts messages in memory — no `ChatMessageRepository` is invoked.
- **Impact:** All chat history is lost on server restart; new players joining see no history.
- **Fix:** Create a `ChatMessageRepository` and save each message in `handleChatMessage()`.

---

### BUG-15: `battle_deployed_units` junction table never populated
- **File:** `database/schema.sql` line ~155 defines `battle_deployed_units`, but `BattleService.deployUnit()` never inserts records.
- **Impact:** Battle history in the DB lacks deployment details. The junction table exists but remains empty.
- **Fix:** Add insert logic when a unit is deployed, tracking `(battle_id, unit_type, quantity)`.

---

## 2. UI/UX IMPROVEMENTS

### UI-01: No connection status indicator
- **Files:** All screens (FXML files)
- **Problem:** Players can't tell if they're connected to the server. If the connection drops, the UI just freezes.
- **Fix:** Add a small status indicator (green dot / red dot) in the resource bar or header, bound to `NetworkClient.isConnected()`.

### UI-02: No loading state during matchmaking
- **File:** `client/src/main/java/com/clashgame/client/controller/DashboardController.java`
- **Problem:** After clicking "Attack", there's no visual feedback while waiting for a match. The button disables for 5 seconds but shows no spinner/progress indicator.
- **Fix:** Show a "Searching for opponent..." overlay with a cancel option.

### UI-03: Missing resource storage capacity display
- **File:** `client/src/main/resources/fxml/dashboard_screen.fxml`
- **Problem:** Dashboard shows "Gold: 500" but not "Gold: 500 / 5000" (current vs max). Players don't know their storage limits.
- **Fix:** Calculate max from Gold Storage / Elixir Storage building capacities and display as "X / Y".

### UI-04: No building info panel on selection
- **File:** `client/src/main/java/com/clashgame/client/controller/BaseController.java`
- **Problem:** When a building is selected on the base screen, there's no info display showing its stats (HP, level, production rate, defense value).
- **Fix:** Add an info panel in the right sidebar that populates when a building is selected.

### UI-05: Train screen doesn't show remaining army capacity
- **File:** `client/src/main/resources/fxml/train_screen.fxml`
- **Problem:** Shows the army list and unit details, but not "Housing Used: 15 / 40". Players must guess how much capacity remains.
- **Fix:** Add a capacity label that updates dynamically: `currentSpace / maxSpace`.

### UI-06: Battle screen doesn't show remaining deployable units
- **File:** `client/src/main/java/com/clashgame/client/controller/BattleController.java`
- **Problem:** The deployment tray shows unit types via ComboBox but the available count per type isn't updated as units are deployed.
- **Fix:** Track remaining units per type and update the ComboBox labels (e.g., "Barbarian (x5)").

### UI-07: No confirmation dialog for building removal
- **File:** `client/src/main/java/com/clashgame/client/controller/BaseController.java`, remove handler
- **Problem:** Clicking "Remove" instantly deletes the building with only a half-cost refund. No "Are you sure?" prompt.
- **Fix:** Add an `Alert` confirmation dialog before sending the remove request.

### UI-08: Leaderboard doesn't auto-refresh
- **File:** `client/src/main/java/com/clashgame/client/controller/LeaderboardController.java`
- **Problem:** Loaded once on screen open. Data goes stale if the player stays on the leaderboard.
- **Fix:** Add a refresh button, or auto-refresh on a timer (e.g., every 30 seconds).

### UI-09: No battle history screen
- **File:** Server has `BattleService.getBattleHistory()`, but no corresponding client screen/button exists.
- **Problem:** Players can't review past attack/defense results.
- **Fix:** Add a "Battle Log" button on dashboard, create `battle_history_screen.fxml` and `BattleHistoryController`.

### UI-10: Chat messages have no timestamps
- **File:** `client/src/main/java/com/clashgame/client/controller/ChatController.java`
- **Problem:** Messages display as "Username: message" with no time info.
- **Fix:** Prepend `[HH:mm]` timestamp from server's message payload (or client-local time).

### UI-11: Login error messages are generic
- **File:** `client/src/main/java/com/clashgame/client/controller/LoginController.java`
- **Problem:** Both "wrong password" and "user not found" show "Invalid credentials". Users can't tell if their username exists.
- **Fix:** (Security trade-off) At minimum, distinguish "Login failed" from network errors. Consider separate messages if security isn't a concern for a university project.

### UI-12: No visual feedback on building placement success/failure
- **File:** `client/src/main/java/com/clashgame/client/controller/BaseController.java`
- **Problem:** When placing a building, if the server rejects it (overlap, insufficient gold), there's no clear error toast shown on the canvas.
- **Fix:** Show a brief red flash or tooltip at the placement location with the error message.

---

## 3. MISSING FEATURES

### FEAT-01: Upgrade timers not implemented
- **Files:** `server/model/Building.java` (`isUpgrading`, `upgradeTimeRemaining`), `server/service/PlayerService.java` (`startUpgrade`/`completeUpgrade`), `server/game/GameStateManager.java` (`gameTick()`)
- **Problem:** Buildings upgrade instantly. The `isUpgrading` flag is set and immediately cleared in `upgradeBuilding()`. The `upgradeTimeRemaining` field exists in the DB schema but is never decremented.
- **Fix:** In `upgradeBuilding()`, set `upgradeTimeRemaining` based on TH level. In `GameStateManager.gameTick()`, decrement timers each second and call `completeUpgrade()` when timer reaches 0.

### FEAT-02: Training timers not implemented
- **Files:** `server/model/UnitType.java` (`trainingTimeSeconds`), `server/service/PlayerService.java` (`trainUnit`)
- **Problem:** Units train instantly. `UnitType.trainingTimeSeconds` values (10–100s) are defined but never used.
- **Fix:** Create a training queue, decrement timers in `GameStateManager.gameTick()`, create units when timers complete.

### FEAT-03: Unit level scaling not implemented
- **Files:** `server/model/Unit.java`, `server/model/UnitType.java`
- **Problem:** Units are always level 1. No method exists to upgrade units or scale stats by level (e.g., HP × 1.1 per level).
- **Fix:** Add a Laboratory building or research system, and apply level scaling in `Unit` constructor.

### FEAT-04: PvP matchmaking queue notification incomplete
- **File:** `server/src/main/java/com/clashgame/server/service/MatchmakingService.java`, `tryMatchFromQueue()`
- **Problem:** Contains `// TODO: Notify both players via GameServer` — matched PvP queue players are never actually notified.
- **Fix:** Inject `GameServer` reference and send `MATCH_FOUND` messages to both players.

### FEAT-05: Gems currency has no use
- **Files:** `server/model/Player.java`, `database/schema.sql`
- **Problem:** Gems are tracked (default 50) but never spent anywhere. No gem shop or speed-up mechanic.
- **Fix:** Add gem spending for instant upgrades, missing-resource purchases, or cosmetic items.

### FEAT-06: No clan system
- **Files:** `server/model/Player.java` has `clanName` field; `database/schema.sql` has no clans table
- **Problem:** Clans are just a string field with no creation, joining, management, or clan chat functionality.
- **Fix:** Create `clans` and `clan_members` tables, clan service, and UI for clan management.

### FEAT-07: No reconnection logic
- **File:** `client/src/main/java/com/clashgame/client/network/NetworkClient.java`
- **Problem:** If the connection drops, the user must close and restart the client. No auto-reconnect attempt.
- **Fix:** Add exponential backoff reconnect loop with UI notification showing reconnection status.

### FEAT-08: No building placement preview
- **File:** `client/src/main/java/com/clashgame/client/controller/BaseController.java`
- **Problem:** When placing a building, there's no ghost/preview image showing where it will be placed and whether it overlaps.
- **Fix:** On mouse hover during placement mode, render a semi-transparent building preview (green if valid, red if invalid).

### FEAT-09: No whisper/private messaging
- **File:** `database/schema.sql` has `channel = 'WHISPER'` and `recipient_id` columns
- **Problem:** Schema supports private messages but no client or server logic implements it.
- **Fix:** Add `/whisper <username> <message>` command parsing in `ChatController`.

### FEAT-10: Heartbeat mechanism exists server-side but not client-side
- **File:** `server/network/GameServer.java` line 229: `case HEARTBEAT -> { /* no-op */ }`
- **Problem:** Server accepts HEARTBEAT messages but the client never sends them. Stale connections aren't detected.
- **Fix:** Add a periodic heartbeat timer in `NetworkClient` (e.g., every 30 seconds).

### FEAT-11: No tutorial or onboarding flow
- **Problem:** New players are dropped into an empty base with 6 default buildings and no guidance.
- **Fix:** Add a first-login tutorial overlay explaining the UI, building placement, training, and attacking.

### FEAT-12: Battle replay system not supported
- **Problem:** No mechanism to record or replay battles. Players can't review their attacks.
- **Fix:** Record `BATTLE_UPDATE` payloads with timestamps and add a replay viewer.

---

## 4. CODE QUALITY

### CQ-01: Manual JSON construction throughout server code
- **Files:** `PlayerService.java` (`getPlayerDataJson`, `getLeaderboard`, `trainUnit`, etc.), `BattleEngine.java` (`buildBattleResultJson`, `sendBattleUpdate`, `buildDefenderBaseJson`)
- **Problem:** All JSON is built via `StringBuilder` and `String.format`. This is error-prone (unescaped characters, wrong format specifiers, fragile string manipulation) and hard to maintain.
- **Fix:** Use Jackson `ObjectMapper.writeValueAsString()` with proper DTOs/response classes.

### CQ-02: Unit stats defined in 4 separate locations
- **Files:**
  1. `server/model/UnitType.java` (enum with base stats) — source of truth
  2. `server/model/Unit.java` (constructor copies from UnitType)
  3. `client/model/Unit.java` (hardcoded in `initializeFromType()`)
  4. `client/controller/TrainController.java` (hardcoded maps for display)
- **Problem:** Four places to update when stats change. Already out of sync (BUG-01).
- **Fix:** Client should receive stats from server (include in UPDATE_BASE or dedicated endpoint). Eliminate client-side hardcoded stat maps entirely.

### CQ-03: GameEngine is a God class (735 lines)
- **File:** `client/src/main/java/com/clashgame/client/game/GameEngine.java`
- **Problem:** Handles authentication, base updates, resource updates, battle state, chat messages, leaderboard, matchmaking — all in one singleton class.
- **Fix:** Extract into focused managers: `AuthManager`, `BaseStateManager`, `BattleStateManager`, `ChatManager`, etc.

### CQ-04: `System.err.println` instead of proper logging on client
- **Files:** `GameEngine.java`, `NetworkClient.java`, multiple controllers
- **Problem:** Error handling uses `System.err.println(...)` instead of a logging framework. No log levels, no file output.
- **Fix:** Add SLF4J + Logback dependency to client `pom.xml` and replace all `System.err.println` with `Logger` calls.

### CQ-05: Magic numbers throughout codebase
- **Examples:**
  - Grid size: `20` (Base.java, both client and server)
  - Battle duration: `180` seconds (BattleEngine, Battle)
  - Defense range: `5` cells (BattleEngine)
  - Tick rate: `20` ticks/sec (BattleEngine)
  - Server port: `9090` (application.properties, NetworkClient)
  - Max clients: `100` (GameServer)
  - Resource generation interval: `60000` ms (ResourceGeneratorThread)
  - Matchmaking trophy range: `200` (MatchmakingService)
  - Training count cap: `50` (PlayerService)
- **Fix:** Extract to configuration constants class or `application.properties`.

### CQ-06: No shared data model / contract between client and server
- **Problem:** Client and server have completely separate model classes (`Player`, `Building`, `Unit`, `Base`) with no shared interface/DTO. Changes to the server's JSON format silently break the client parser.
- **Fix:** Create a `common` Maven module with shared DTOs that both client and server depend on.

### CQ-07: Potential NullPointerException in JSON parsing
- **Files:** `GameEngine.java` `handleBaseUpdate()`, `BattleService.java`, `PlayerService.java`
- **Problem:** Code like `bNode.get("gridX").asInt()` will throw NPE if `gridX` is missing. Some fields use `bNode.has("x")` checks, but `gridX` and `gridY` do not.
- **Fix:** Either validate all required fields before access, or use `path("gridX")` which returns a `MissingNode` instead of `null`.

### CQ-08: `spring.jpa.open-in-view=false` with lazy collections
- **File:** `server/src/main/resources/application.properties`
- **Problem:** Open-in-view is disabled (good practice), but accessing lazy-loaded collections like `player.getBase().getBuildings()` outside a `@Transactional` method will throw `LazyInitializationException`.
- **Fix:** Ensure all service methods that access lazy collections are `@Transactional`, or use `JOIN FETCH` in repository queries.

### CQ-09: Thread pool not bounded for battles
- **File:** `server/src/main/java/com/clashgame/server/game/GameStateManager.java`
- **Problem:** Uses `Executors.newCachedThreadPool()` for battle threads. Under load, this creates unbounded threads.
- **Fix:** Use `Executors.newFixedThreadPool(maxConcurrentBattles)` with a configurable limit.

### CQ-10: No proper shutdown hooks
- **File:** `server/src/main/java/com/clashgame/server/network/GameServer.java`
- **Problem:** `ServerSocket`, `ExecutorService`, and `ClientHandler` threads are never cleanly shut down on application stop. Could leave port 9090 occupied.
- **Fix:** Implement `DisposableBean` or `@PreDestroy` to close the server socket, shut down the executor, and disconnect all clients.

### CQ-11: Building renderer logic duplicated between BaseController and BattleController
- **Files:** `BaseController.java` (1059 lines), `BattleController.java` (1205 lines)
- **Problem:** Both controllers have near-identical building rendering code (colors, shapes, labels for all 12 building types), duplicated with minor variations (battle version has damage dimming).
- **Fix:** Extract a shared `BuildingRenderer` utility class with a `render(GraphicsContext, Building, boolean isBattle)` method.

---

## 5. GAMEPLAY IMPROVEMENTS

### GAME-01: Walls have no tactical function
- **Problem:** `WALL` building type exists with stats, but units ignore walls entirely. `BattleEngine` uses `unit.moveToward(target)` which moves in a straight line with no obstacle avoidance.
- **Fix:** Implement simple pathfinding (e.g., A* on the 20×20 grid) that routes units around wall segments.

### GAME-02: No shield system after defense
- **Problem:** A player can be repeatedly attacked with no respite. In Clash of Clans, defenders receive a time-limited shield after being attacked.
- **Fix:** After receiving an attack, grant the defender a shield (e.g., 4–16 hours based on destruction %) that prevents further attacks. Track via `shield_until` timestamp on `players` table.

### GAME-03: Default base layout is weak
- **File:** `server/src/main/java/com/clashgame/server/service/PlayerService.java`, `createDefaultBase()`
- **Problem:** New players start with 6 buildings placed in a rough cluster. The layout provides no tactical advantage.
- **Fix:** Design a better starter layout that demonstrates good base design (e.g., TH centered, defenses covering resources).

### GAME-04: Healer targeting could be smarter
- **File:** `server/src/main/java/com/clashgame/server/game/BattleEngine.java`, Healer AI tick
- **Problem:** Healer heals "any damaged ally" — just the first one found. Should prioritize the most damaged or most valuable unit.
- **Fix:** Sort potential heal targets by `(maxHp - currentHp)` descending and heal the most-damaged unit.

### GAME-05: Matchmaking only considers trophies
- **File:** `server/src/main/java/com/clashgame/server/service/MatchmakingService.java`
- **Problem:** Opponent matching with ±200 trophies can pair a TH1 with a TH5 if they have similar trophies. Extremely unfair matches.
- **Fix:** Add Town Hall level as a secondary matching criterion (e.g., ±2 TH levels).

### GAME-06: Resource generation doesn't account for offline time
- **File:** `server/src/main/java/com/clashgame/server/game/ResourceGeneratorThread.java`
- **Problem:** Resources are generated via `@Scheduled(fixedRate = 60000)`, but only for online players (checks `isOnline`). Offline players get zero generation — no "catch-up" mechanic.
- **Fix:** On login, calculate `(currentTime - lastLogin) * productionRate` and credit accumulated offline resources (capped by storage).

### GAME-07: Loot calculation uses stale defender resources
- **File:** `server/src/main/java/com/clashgame/server/model/Battle.java`, `calculateLoot()`
- **Problem:** `defender.getGold()` and `defender.getElixir()` reference the Player entity loaded at battle start. If resources change during the 3-minute battle, loot is based on outdated values.
- **Fix:** Reload the defender's current resources at battle end before calculating loot.

### GAME-08: No war/revenge mechanics
- **Problem:** Players can't rematch someone who attacked them. No "Revenge" button in battle history.
- **Fix:** Add a revenge action in battle history that bypasses matchmaking and directly targets the previous attacker.

### GAME-09: Star thresholds are too high
- **File:** `server/src/main/java/com/clashgame/server/model/Battle.java`, `calculateStars()`
- **Problem:** 1 star requires 50% destruction. With limited armies and strong defenses, getting even 1 star can be very difficult.
- **Fix:** Consider alternative thresholds (e.g., 1 star at 30%, 2 at 60%, 3 at 100%) or award 1 star for destroying the Town Hall regardless of destruction %.

### GAME-10: No laboratory or research system for unit upgrades
- **Problem:** Units are always level 1. There's no in-game way to upgrade troops.
- **Fix:** Add a Laboratory building that allows researching unit upgrades using elixir, with research timers.

---

## Summary Statistics

| Category              | Count |
|-----------------------|-------|
| **Bugs**              | 15    |
| **UI/UX Improvements**| 12    |
| **Missing Features**  | 12    |
| **Code Quality**      | 11    |
| **Gameplay**          | 10    |
| **Total Issues**      | **60**|

### Priority Recommendations (Top 10 Quick Wins)

1. **BUG-01** — Fix TrainController stat maps (5-minute fix, high user-facing impact)
2. **BUG-02** — Parse `storageCapacity` in `handleBaseUpdate` (1-line fix)
3. **BUG-03** — Fix trophy exchange formula (small logic change)
4. **BUG-08** — Replace Thread with PauseTransition (2-minute fix)
5. **BUG-07** — Add `dispose()` to BattleController to stop AnimationTimer
6. **UI-03** — Add resource capacity display to dashboard
7. **UI-05** — Show army capacity on train screen
8. **CQ-02** — Eliminate duplicated unit stats (reduces future sync bugs)
9. **CQ-05** — Extract magic numbers to constants
10. **GAME-06** — Add offline resource catch-up on login
