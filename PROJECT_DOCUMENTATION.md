# 🏰 Multiplayer Online Strategy Game (Mini Clash of Clans Style)

## Advanced Object-Oriented Programming — University Project Documentation

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [System Architecture](#2-system-architecture)
3. [Client-Server Architecture](#3-client-server-architecture)
4. [Project Folder Structure](#4-project-folder-structure)
5. [Class Diagram & OOP Design](#5-class-diagram--oop-design)
6. [Code Skeleton Explanations](#6-code-skeleton-explanations)
7. [JavaFX UI Structure](#7-javafx-ui-structure)
8. [Networking Design](#8-networking-design)
9. [Multithreading Design](#9-multithreading-design)
10. [Database Design](#10-database-design)
11. [Game Mechanics & Logic](#11-game-mechanics--logic)
12. [Battle System](#12-battle-system)
13. [UI Layout Design](#13-ui-layout-design)
14. [Advanced Features](#14-advanced-features)
15. [Final Project Summary & How to Run](#15-final-project-summary--how-to-run)

---

## 1. Project Overview

### 1.1 Introduction

This project is a **Multiplayer Online Strategy Game** inspired by Clash of Clans, built as an advanced Object-Oriented Programming university project in Java. Players build and defend their bases, train armies, and attack other players in real-time PvP battles.

### 1.2 Key Features

| Feature | Description |
|---------|-------------|
| **Base Building** | 20×20 grid system with 12 building types (Town Hall, Gold Mine, Cannon, etc.) |
| **Army Training** | 7 unit types with unique stats (Barbarian, Archer, Giant, Dragon, etc.) |
| **Real-Time Battles** | Tick-based battle engine running at 20 ticks/sec on the server |
| **Matchmaking** | Trophy-based matchmaking with ±200 range and cooldown |
| **Resource Economy** | Gold, Elixir, and Gems with automated production |
| **Leaderboard** | Top 20 players ranked by trophies |
| **Global Chat** | Real-time messaging between connected players |
| **Persistent Data** | MySQL/PostgreSQL database with JPA/Hibernate |

### 1.3 Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Server Framework | Spring Boot 3.2.3 |
| Client UI | JavaFX 17 with FXML |
| Networking | TCP Sockets (real-time) + REST API (queries) |
| Database | MySQL 8.0 / PostgreSQL 16 |
| ORM | JPA / Hibernate |
| Build Tool | Apache Maven |
| Serialization | Java ObjectStreams + Jackson JSON |
| Security | BCrypt password hashing |

### 1.4 Academic Objectives

This project demonstrates mastery of:

- **OOP Pillars**: Encapsulation, Inheritance, Polymorphism, Abstraction
- **Design Patterns**: MVC, Singleton, Observer, Factory Method, Strategy
- **SOLID Principles**: Single Responsibility, Open/Closed, Dependency Inversion
- **Concurrency**: Threads, thread pools, synchronization, concurrent collections
- **Networking**: TCP sockets, client-server protocol, message serialization
- **Database**: Relational design, ER modeling, JPA entity mapping
- **UI Design**: JavaFX with FXML separation, data binding, Canvas rendering

---

## 2. System Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        GAME SYSTEM                              │
│                                                                 │
│  ┌──────────────┐          TCP Socket           ┌────────────┐  │
│  │ JavaFX Client │ ◄──── (Port 9090) ─────────► │ Game Server│  │
│  │              │          REST API              │ (Spring    │  │
│  │  - UI Views  │ ◄──── (Port 8080) ─────────► │   Boot)    │  │
│  │  - Game      │                                │            │  │
│  │    Engine    │                                │  - Network │  │
│  │  - Network   │                                │  - Battle  │  │
│  │    Client    │                                │    Engine  │  │
│  └──────────────┘                                │  - Services│  │
│                                                  │            │  │
│                                                  │     ▼      │  │
│                                                  │ ┌────────┐ │  │
│                                                  │ │Database│ │  │
│                                                  │ │(MySQL) │ │  │
│                                                  │ └────────┘ │  │
│                                                  └────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Layered Architecture

**Server Side (4 Layers):**
```
┌─────────────────────────────┐
│     Controller Layer        │  ← REST endpoints (PlayerController)
├─────────────────────────────┤
│     Service Layer           │  ← Business logic (PlayerService, BattleService, MatchmakingService)
├─────────────────────────────┤
│     Repository Layer        │  ← Data access (JPA Repositories)
├─────────────────────────────┤
│     Model Layer             │  ← JPA Entities (Player, Base, Building, Unit, Battle)
└─────────────────────────────┘
```

**Client Side (MVC):**
```
┌─────────────────────────────┐
│     View (FXML + CSS)       │  ← UI layouts and styles
├─────────────────────────────┤
│     Controller              │  ← Event handlers (LoginController, BaseController, etc.)
├─────────────────────────────┤
│     Model                   │  ← JavaFX property-based models
├─────────────────────────────┤
│     Network + Game Engine   │  ← Socket communication + game loop
└─────────────────────────────┘
```

### 2.3 Design Patterns Used

| Pattern | Where Used | Purpose |
|---------|-----------|---------|
| **MVC** | Entire client (FXML views + Controllers + Models) | Separation of concerns |
| **Singleton** | `GameEngine.getInstance()` | Single game state manager on client |
| **Observer** | JavaFX Properties + bindings, message handlers | Reactive UI updates |
| **Factory Method** | `GameMessage.createXxx()` static methods | Standardized message creation |
| **Strategy** | `BattleEngine` tick phases (unit AI, defense AI) | Swappable combat algorithms |
| **Repository** | Spring Data JPA repositories | Data access abstraction |
| **Template Method** | Building/Unit `initializeStats()` | Type-specific initialization |
| **Command** | `GameMessage.MessageType` enum dispatch | Message-driven actions |

---

## 3. Client-Server Architecture

### 3.1 Dual Communication Channels

The system uses **two communication channels** between client and server:

#### Channel 1: TCP Socket (Port 9090) — Real-Time

Used for all real-time game interactions:
- Battle updates (20 ticks/sec)
- Resource generation notifications
- Chat messages
- Player status updates
- Building placement / unit training
- Matchmaking requests

**Protocol**: Java `ObjectInputStream` / `ObjectOutputStream` with `GameMessage` objects.

#### Channel 2: REST API (Port 8080) — Query/Response

Used for non-real-time operations:
- Player registration
- Login authentication
- Leaderboard queries
- Battle history

**Protocol**: HTTP JSON via Spring Boot `@RestController`.

### 3.2 Connection Lifecycle

```
Client                              Server
  │                                   │
  │──── TCP Connect (9090) ──────────►│  Socket accepted, ClientHandler created
  │                                   │
  │──── JOIN_GAME {user, pass} ──────►│  PlayerService.authenticate()
  │◄─── LOGIN_SUCCESS {playerData} ──│  Player loaded, marked online
  │                                   │
  │──── PLACE_BUILDING {...} ────────►│  PlayerService.placeBuilding()
  │◄─── BASE_UPDATE {baseData} ──────│  Updated base sent back
  │                                   │
  │──── FIND_OPPONENT ───────────────►│  MatchmakingService.findOpponent()
  │◄─── OPPONENT_FOUND {opponent} ───│  Match found
  │                                   │
  │──── ATTACK_PLAYER {targetId} ────►│  BattleService.initiateBattle()
  │◄─── BATTLE_START {battleData} ───│  BattleEngine thread started
  │                                   │
  │──── DEPLOY_UNIT {type, x, y} ────►│  BattleEngine.deployUnit()
  │◄─── BATTLE_UPDATE (20/sec) ──────│  Tick-based state broadcast
  │◄─── BATTLE_END {results} ────────│  Battle concluded, rewards applied
  │                                   │
  │◄─── RESOURCE_UPDATE (60sec) ─────│  ResourceGeneratorThread notification
  │                                   │
  │──── LEAVE_GAME ──────────────────►│  Player marked offline, socket closed
  │                                   │
```

### 3.3 Message Protocol

All real-time communication uses `GameMessage` objects:

```java
public class GameMessage implements Serializable {
    private MessageType type;    // Enum: 25+ message types
    private String senderId;     // Source player ID
    private String receiverId;   // Target player ID (or "all")
    private String payload;      // JSON-encoded data
    private long timestamp;      // System.currentTimeMillis()
}
```

**Message Types Catalog:**

| Category | Message Types |
|----------|--------------|
| **Auth** | `JOIN_GAME`, `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LEAVE_GAME` |
| **Base** | `PLACE_BUILDING`, `UPGRADE_BUILDING`, `BASE_UPDATE` |
| **Army** | `TRAIN_UNIT`, `UNIT_TRAINED`, `ARMY_UPDATE` |
| **Battle** | `FIND_OPPONENT`, `OPPONENT_FOUND`, `ATTACK_PLAYER`, `DEPLOY_UNIT`, `BATTLE_START`, `BATTLE_UPDATE`, `BATTLE_END` |
| **Resources** | `RESOURCE_UPDATE`, `COLLECT_RESOURCES` |
| **Social** | `CHAT_MESSAGE`, `PLAYER_STATUS`, `LEADERBOARD_UPDATE` |
| **System** | `ERROR`, `SERVER_MESSAGE`, `PING`, `PONG` |

---

## 4. Project Folder Structure

```
Online Strategy Game (Clash of Clans Style Mini)/
│
├── server/                                          # Spring Boot Server
│   ├── pom.xml                                      # Maven build (Spring Boot 3.2.3)
│   └── src/main/
│       ├── java/com/clashgame/server/
│       │   ├── ClashGameServerApplication.java      # @SpringBootApplication main
│       │   ├── model/                               # JPA Entity classes
│       │   │   ├── Player.java                      # Player entity (1:1 Base, 1:N Unit)
│       │   │   ├── Base.java                        # Base entity (1:N Building)
│       │   │   ├── Building.java                    # Building entity with stats
│       │   │   ├── BuildingType.java                # Enum: 12 building types
│       │   │   ├── Unit.java                        # Unit entity with battle logic
│       │   │   ├── UnitType.java                    # Enum: 7 unit types
│       │   │   ├── Battle.java                      # Battle entity with lifecycle
│       │   │   └── GameMessage.java                 # Serializable protocol message
│       │   ├── network/                             # Socket server
│       │   │   ├── GameServer.java                  # TCP server, client management
│       │   │   └── ClientHandler.java               # Per-client thread handler
│       │   ├── game/                                # Game engine
│       │   │   ├── BattleEngine.java                # Tick-based battle simulation
│       │   │   ├── ResourceGeneratorThread.java     # Automated resource production
│       │   │   └── GameStateManager.java            # Active battle management
│       │   ├── service/                             # Business logic
│       │   │   ├── PlayerService.java               # Auth, base, unit management
│       │   │   ├── BattleService.java               # Battle lifecycle
│       │   │   └── MatchmakingService.java          # Trophy-based matchmaking
│       │   ├── controller/                          # REST API
│       │   │   └── PlayerController.java            # /api/players endpoints
│       │   ├── repository/                          # JPA Repositories
│       │   │   ├── PlayerRepository.java
│       │   │   ├── BaseRepository.java
│       │   │   ├── BuildingRepository.java
│       │   │   ├── UnitRepository.java
│       │   │   └── BattleRepository.java
│       │   └── config/
│       │       └── GameConfig.java                  # BCrypt bean configuration
│       └── resources/
│           └── application.properties               # DB config, server port
│
├── client/                                          # JavaFX Client
│   ├── pom.xml                                      # Maven build (JavaFX 17)
│   └── src/main/
│       ├── java/
│       │   ├── module-info.java                     # JavaFX module declarations
│       │   └── com/clashgame/client/
│       │       ├── GameClient.java                  # JavaFX Application entry point
│       │       ├── model/                           # JavaFX property models
│       │       │   ├── Player.java                  # Observable player properties
│       │       │   ├── Base.java                    # Grid + ObservableList<Building>
│       │       │   ├── Building.java                # Color-coded building model
│       │       │   ├── Unit.java                    # Unit with battle position
│       │       │   └── BattleResult.java            # Star display data
│       │       ├── network/
│       │       │   └── NetworkClient.java           # Socket client with handlers
│       │       ├── game/
│       │       │   └── GameEngine.java              # Singleton game state + loop
│       │       ├── controller/                      # FXML Controllers
│       │       │   ├── LoginController.java         # Login/Register screen
│       │       │   ├── DashboardController.java     # Main menu + resource display
│       │       │   ├── BaseController.java          # Base builder (Canvas)
│       │       │   ├── BattleController.java        # Battle HUD + unit deployment
│       │       │   ├── LeaderboardController.java   # TableView leaderboard
│       │       │   └── ChatController.java          # Real-time chat panel
│       │       └── util/
│       │           └── UIUtils.java                 # Alert dialogs, grid drawing
│       └── resources/
│           ├── fxml/                                # FXML Layouts
│           │   ├── login_screen.fxml                # Dark-themed login form
│           │   ├── dashboard_screen.fxml            # Resource bar + navigation
│           │   ├── base_screen.fxml                 # 600×600 canvas + side panel
│           │   ├── battle_screen.fxml               # Battle HUD + unit tray
│           │   ├── leaderboard_screen.fxml          # TableView layout
│           │   └── chat_panel.fxml                  # Embeddable chat component
│           └── css/
│               └── game_style.css                   # Medieval dark theme
│
├── database/
│   └── schema.sql                                   # DDL + sample data + views
│
└── PROJECT_DOCUMENTATION.md                         # ← This comprehensive document
```

---

## 5. Class Diagram & OOP Design

### 5.1 Server-Side Class Relationships

```
                        ┌──────────────────┐
                        │  <<Application>> │
                        │ ClashGameServer   │
                        │  Application      │
                        └────────┬─────────┘
                                 │ creates
                    ┌────────────┼────────────┐
                    ▼            ▼             ▼
           ┌──────────┐  ┌────────────┐  ┌─────────────────┐
           │GameServer │  │  Services  │  │   Controllers   │
           │(network)  │  │ (business) │  │   (REST API)    │
           └────┬──────┘  └─────┬──────┘  └────────┬────────┘
                │               │                   │
       creates ┌┘         ┌─────┼─────┐            uses
               ▼          ▼     ▼     ▼             │
     ┌─────────────┐  ┌─────┐┌────┐┌──────┐  ┌─────┴─────┐
     │ClientHandler │  │Play-││Bat-││Match-│  │ Player    │
     │(per client)  │  │er   ││tle ││making│  │ Controller│
     └──────────────┘  │Svc  ││Svc ││Svc   │  └───────────┘
                       └──┬──┘└─┬──┘└──┬───┘
                          │     │      │         uses
                    ┌─────┴─────┴──────┴──────┐
                    ▼                          ▼
              ┌──────────────┐         ┌──────────────┐
              │ Repositories │         │  Game Engine  │
              │ (JPA)        │         │  Components   │
              └──────┬───────┘         └───────┬──────┘
                     │                    ┌────┼────┐
                     ▼                    ▼    ▼    ▼
              ┌──────────────┐    ┌──────┐┌───┐┌────────┐
              │   Models     │    │Battle││Res-││GameState│
              │ (JPA Entities│    │Engine││Gen ││Manager  │
              └──────────────┘    └──────┘└───┘└────────┘
```

### 5.2 Entity Relationships (OOP)

```
 ┌──────────────────────────────────────────────────────────────────┐
 │                    ENTITY RELATIONSHIPS                          │
 │                                                                  │
 │  ┌────────┐                                                      │
 │  │ Player │ ── Composition (1:1) ──► Base                       │
 │  │        │ ── Aggregation  (1:N) ──► Unit                      │
 │  │        │ ── Association  (1:N) ──► Battle (as attacker)      │
 │  │        │ ── Association  (1:N) ──► Battle (as defender)      │
 │  └────────┘                                                      │
 │                                                                  │
 │  ┌────────┐                                                      │
 │  │  Base  │ ── Composition (1:N) ──► Building (orphanRemoval)   │
 │  └────────┘                                                      │
 │                                                                  │
 │  ┌──────────┐                                                    │
 │  │ Building │ ── Dependencies ──► BuildingType (enum)           │
 │  └──────────┘                                                    │
 │                                                                  │
 │  ┌────────┐                                                      │
 │  │  Unit  │ ── Dependencies ──► UnitType (enum)                 │
 │  └────────┘                                                      │
 │                                                                  │
 │  ┌────────┐                                                      │
 │  │ Battle │ ── Association ──► Player (attacker)                │
 │  │        │ ── Association ──► Player (defender)                │
 │  │        │ ── Aggregation ──► UnitType (deployed units)        │
 │  └────────┘                                                      │
 └──────────────────────────────────────────────────────────────────┘
```

### 5.3 Inheritance & Interfaces

While the current design favors **composition over inheritance** (a modern OOP best practice), the following design hierarchies are present:

```
java.io.Serializable
  └── GameMessage                     # Network protocol object

java.lang.Enum
  ├── BuildingType                    # 12 building categories
  ├── UnitType                        # 7 unit types with base stats
  ├── Battle.BattleStatus             # PENDING → IN_PROGRESS → COMPLETED
  ├── BuildingType.Category           # CORE, RESOURCE, STORAGE, DEFENSE, ARMY
  └── GameMessage.MessageType         # 25+ message type codes

java.lang.Runnable
  ├── ClientHandler                   # Per-client network thread
  ├── BattleEngine                    # Battle simulation thread
  └── ResourceGeneratorThread         # Resource production daemon

javafx.application.Application
  └── GameClient                      # JavaFX application entry point
```

### 5.4 OOP Principles Demonstrated

#### Encapsulation
- All model fields are `private` with getter/setter methods
- Resource spending uses validation methods (`spendGold(amount)` returns boolean)
- Building grid overlap detection is encapsulated in `Base.canPlaceBuilding()`
- Password hashing is hidden behind `PlayerService.registerPlayer()`

#### Polymorphism
- `BuildingType` and `UnitType` enums provide polymorphic initialization via `initializeStats()`
- `GameMessage.MessageType` drives polymorphic message handling in `GameServer.processMessage()`
- JPA repositories share `JpaRepository<T, ID>` interface with type-specific query methods

#### Abstraction
- `JpaRepository` abstracts all CRUD operations
- `NetworkClient` abstracts socket communication behind simple method calls (`login()`, `attackPlayer()`)
- `GameEngine` abstracts client-side game state behind a Singleton facade

#### Composition
- `Player` **composes** `Base` (cascade ALL, if player deleted → base deleted)
- `Base` **composes** `Building` list (orphanRemoval = true)
- `GameEngine` **composes** `NetworkClient` and manages the game loop

#### Aggregation
- `Player` **aggregates** `Unit` list (units can exist independently in theory)
- `Battle` **aggregates** `UnitType` list (deployed units reference)

---

## 6. Code Skeleton Explanations

### 6.1 Server Model Classes

#### `Player.java` — The Central Entity
```java
@Entity @Table(name = "players")
public class Player {
    @Id @GeneratedValue private Long id;
    private String username, passwordHash;
    private int level, xp, gold, elixir, gems, trophies;

    @OneToOne(mappedBy = "player", cascade = ALL) private Base base;      // Composition
    @OneToMany(mappedBy = "player") private List<Unit> army;              // Aggregation

    // Resource management with validation
    public boolean spendGold(int amount) { ... }     // Returns false if insufficient
    public void addExperience(int xp) { ... }        // Auto-levels when XP threshold met
    public int getTotalArmyStrength() { ... }         // Sum of unit attack power
}
```

**Key Design Decisions:**
- Resources start at 500 gold, 500 elixir, 50 gems (balanced for new players)
- XP threshold: `level * 1000` (Level 2 = 2000 XP, Level 3 = 3000 XP, etc.)
- Trophies can go negative (minimum 0 enforced in `adjustTrophies()`)

#### `Base.java` — Grid-Based Layout
```java
@Entity @Table(name = "bases")
public class Base {
    @OneToMany(cascade = ALL, orphanRemoval = true) private List<Building> buildings;
    private int gridWidth = 20, gridHeight = 20;

    public boolean placeBuilding(Building b, int x, int y) { ... }  // Overlap check
    public double getGoldProductionRate() { ... }                    // Sum of all mines
    public int getTotalHitPoints() { ... }                           // Sum for battle
}
```

#### `Building.java` — Stats & Upgrades
```java
@Entity @Table(name = "buildings")
public class Building {
    @Enumerated(STRING) private BuildingType type;
    private int level, hitPoints, defenseValue;

    public void initializeStats() { ... }      // Switch on BuildingType for base stats
    public void completeUpgrade() { ... }      // 1.2x HP, 1.15x defense per level
    public void takeDamage(int damage) { ... } // For battle engine
}
```

#### `Unit.java` — Battle Participant
```java
@Entity @Table(name = "units")
public class Unit {
    @Enumerated(STRING) private UnitType type;
    @Transient private double battleX, battleY;          // Only during battle
    @Transient private Building targetBuilding;          // Current attack target

    public void attackBuilding(Building target) { ... }  // Apply damage
    public void moveToward(double tx, double ty) { ... } // Simple pathfinding
}
```

### 6.2 Server Network Classes

#### `GameServer.java` — Connection Hub
```java
@Component
public class GameServer {
    private final ConcurrentHashMap<String, ClientHandler> connectedClients;
    private final ExecutorService clientThreadPool;

    @PostConstruct public void startServer() { ... }     // Listener thread
    public void processMessage(GameMessage msg) { ... }  // 25+ message type switch
    public void sendToPlayer(String id, GameMessage msg); // Targeted delivery
    public void broadcastMessage(GameMessage msg);        // All players
}
```

**Thread Safety**: Uses `ConcurrentHashMap` for client registry, `synchronized` blocks for registration/unregistration.

#### `ClientHandler.java` — Per-Client Thread
```java
public class ClientHandler implements Runnable {
    private final ObjectInputStream in;
    private final ObjectOutputStream out;

    @Override public void run() {
        while (running) {
            GameMessage msg = (GameMessage) in.readObject();  // Blocking read
            gameServer.processMessage(msg);                   // Route to server
        }
    }

    public synchronized void sendMessage(GameMessage msg) {   // Thread-safe write
        out.writeObject(msg);
        out.flush();
    }
}
```

### 6.3 Server Game Engine

#### `BattleEngine.java` — Real-Time Simulation
```java
public class BattleEngine implements Runnable {
    private static final int TICKS_PER_SECOND = 20;
    private final CopyOnWriteArrayList<Unit> attackerUnits;   // Thread-safe list
    private final CopyOnWriteArrayList<Building> defenderBuildings;

    @Override public void run() {
        while (running && !isTimeUp()) {
            tick();
            Thread.sleep(1000 / TICKS_PER_SECOND);  // 50ms per tick
        }
        endBattle();
    }

    private void tick() {
        processUnitAI();      // Phase 1: Units find targets, move, attack
        processDefenseAI();   // Phase 2: Defensive buildings fire at nearby units
        cleanupDestroyed();   // Phase 3: Remove dead units/buildings
        broadcastState();     // Phase 4: Send update to clients
    }
}
```

**Battle Tick Phases:**
1. **Unit AI**: Each alive unit finds nearest non-destroyed building → moves toward it → attacks if in range (≤1.5 units)
2. **Defense AI**: Each defensive building (CANNON, ARCHER_TOWER, MORTAR) finds nearest unit within range (5.0 units) → deals damage
3. **Cleanup**: Remove units with HP ≤ 0, mark buildings as destroyed
4. **Broadcast**: Send `BATTLE_UPDATE` message with current state

### 6.4 Client Architecture

#### `GameEngine.java` — Singleton Game Manager
```java
public class GameEngine {
    private static final GameEngine INSTANCE = new GameEngine();
    private final NetworkClient networkClient;
    private final AnimationTimer gameLoop;
    private final ObjectProperty<Player> currentPlayer;

    // Registers handlers for all 25+ message types
    private void registerMessageHandlers() {
        networkClient.registerHandler(LOGIN_SUCCESS, this::handleLoginSuccess);
        networkClient.registerHandler(BATTLE_UPDATE, this::handleBattleUpdate);
        // ... etc
    }

    // Screen navigation via callback
    private Consumer<String> screenNavigator;
    public void navigateToScreen(String screen) {
        Platform.runLater(() -> screenNavigator.accept(screen));
    }
}
```

#### `NetworkClient.java` — Socket Client
```java
public class NetworkClient {
    private final ConcurrentHashMap<MessageType, Consumer<GameMessage>> handlers;

    public void connect(String host, int port) { ... }

    // Receiver thread runs on background, dispatches to UI thread
    private void startReceiverThread() {
        new Thread(() -> {
            while (connected) {
                GameMessage msg = (GameMessage) inputStream.readObject();
                Consumer<GameMessage> handler = handlers.get(msg.getType());
                if (handler != null) {
                    Platform.runLater(() -> handler.accept(msg));  // UI-safe
                }
            }
        }).start();
    }
}
```

---

## 7. JavaFX UI Structure

### 7.1 Screen Navigation Flow

```
                    ┌──────────┐
                    │  Login   │
                    │  Screen  │
                    └────┬─────┘
                         │ Login Success
                         ▼
                    ┌──────────┐
            ┌───── │Dashboard │ ─────┐
            │      │(Main Menu)│      │
            │      └──────────┘      │
            ▼           │ ▼          ▼
     ┌──────────┐ ┌─────────┐ ┌────────────┐
     │   Base   │ │ Battle  │ │Leaderboard │
     │ Builder  │ │ Screen  │ │            │
     └──────────┘ └─────────┘ └────────────┘
            │           │           │
            └───────────┴───────────┘
                   Back to Dashboard

     ┌──────────┐
     │   Chat   │  ← Embeddable panel (can overlay any screen)
     │  Panel   │
     └──────────┘
```

### 7.2 FXML + Controller Binding

Each screen follows the MVC pattern:

| Screen | FXML File | Controller | Model |
|--------|-----------|-----------|-------|
| Login | `login_screen.fxml` | `LoginController` | — |
| Dashboard | `dashboard_screen.fxml` | `DashboardController` | `Player` |
| Base Builder | `base_screen.fxml` | `BaseController` | `Base`, `Building` |
| Battle | `battle_screen.fxml` | `BattleController` | `Unit`, `Building` |
| Leaderboard | `leaderboard_screen.fxml` | `LeaderboardController` | `LeaderboardEntry` |
| Chat | `chat_panel.fxml` | `ChatController` | `ObservableList<String>` |

### 7.3 JavaFX Property Binding

Client models use JavaFX properties for automatic UI updates:

```java
// Model (Player.java)
private final IntegerProperty gold = new SimpleIntegerProperty(0);
public IntegerProperty goldProperty() { return gold; }

// Controller (DashboardController.java)
goldLabel.textProperty().bind(player.goldProperty().asString("Gold: %,d"));
// When gold changes → label updates automatically (Observer Pattern)
```

### 7.4 Canvas-Based Rendering

The Base Builder and Battle Screen use `Canvas` for custom rendering:

```java
// BaseController.java — Drawing the 20×20 grid
private void drawBase() {
    GraphicsContext gc = baseCanvas.getGraphicsContext2D();
    double cellSize = baseCanvas.getWidth() / 20;

    // Draw grid lines
    gc.setStroke(Color.DARKGREEN);
    for (int i = 0; i <= 20; i++) {
        gc.strokeLine(i * cellSize, 0, i * cellSize, height);
        gc.strokeLine(0, i * cellSize, width, i * cellSize);
    }

    // Draw each building
    for (Building b : base.getBuildings()) {
        gc.setFill(b.getColor());  // Type-specific color
        gc.fillRect(b.getGridX() * cellSize, b.getGridY() * cellSize,
                     b.getWidth() * cellSize, b.getHeight() * cellSize);
        // Draw level badge, health bar, label
    }
}
```

---

## 8. Networking Design

### 8.1 Socket Protocol

```
┌───────────────────────────────────────────────────────┐
│                  GameMessage Object                    │
├───────────┬──────────┬───────────┬──────────┬────────┤
│ type      │ senderId │receiverId │ payload  │timestamp│
│ (Enum)    │ (String) │ (String)  │ (JSON)   │ (long)  │
├───────────┴──────────┴───────────┴──────────┴────────┤
│            Serialized via ObjectOutputStream           │
└───────────────────────────────────────────────────────┘
```

### 8.2 Message Flow Examples

#### Login Flow
```
Client → Server:  GameMessage(JOIN_GAME, "newuser", null, {"username":"x","password":"y"})
Server → Client:  GameMessage(LOGIN_SUCCESS, "server", "newuser", {"player":{...json...}})
      or
Server → Client:  GameMessage(LOGIN_FAILED, "server", "newuser", {"error":"Invalid creds"})
```

#### Battle Flow
```
Client → Server:  GameMessage(FIND_OPPONENT, "player1", null, null)
Server → Client:  GameMessage(OPPONENT_FOUND, "server", "player1", {"opponent":{...}})
Client → Server:  GameMessage(ATTACK_PLAYER, "player1", "player2", null)
Server → Both:    GameMessage(BATTLE_START, "server", "all", {"battleId":"..."})
Client → Server:  GameMessage(DEPLOY_UNIT, "player1", null, {"type":"BARBARIAN","x":0,"y":5})
Server → Both:    GameMessage(BATTLE_UPDATE, "server", "all", {"units":[...],"buildings":[...]}) × 20/sec
Server → Both:    GameMessage(BATTLE_END, "server", "all", {"stars":2,"destruction":78.5,...})
```

### 8.3 Thread-Safe Communication

```java
// Server-side: Synchronized message sending prevents interleaved writes
public class ClientHandler {
    public synchronized void sendMessage(GameMessage msg) {
        objectOutputStream.writeObject(msg);
        objectOutputStream.flush();
    }
}

// Client-side: Platform.runLater ensures UI-safe callback execution
public class NetworkClient {
    private void dispatchMessage(GameMessage msg) {
        Consumer<GameMessage> handler = handlers.get(msg.getType());
        Platform.runLater(() -> handler.accept(msg));  // JavaFX Application Thread
    }
}
```

---

## 9. Multithreading Design

### 9.1 Thread Architecture

```
SERVER THREADS:
┌─────────────────────────────────────────────────────────────────┐
│ Main Thread (Spring Boot)                                       │
│   ├── Socket Acceptor Thread (GameServer - listener loop)      │
│   ├── Client Thread Pool (ExecutorService - cached)            │
│   │     ├── ClientHandler Thread #1  (Player "DragonSlayer")   │
│   │     ├── ClientHandler Thread #2  (Player "WarChief")       │
│   │     └── ClientHandler Thread #N  (up to 100 clients)      │
│   ├── Battle Thread Pool (ExecutorService - cached)            │
│   │     ├── BattleEngine Thread #1   (Battle "abc-123")       │
│   │     └── BattleEngine Thread #N                              │
│   ├── Resource Generator Thread (daemon, 60s tick)             │
│   └── Game Tick Scheduler (ScheduledExecutorService, 1/sec)    │
└─────────────────────────────────────────────────────────────────┘

CLIENT THREADS:
┌─────────────────────────────────────────────────────────────────┐
│ JavaFX Application Thread (UI rendering, event handling)       │
│   ├── Network Receiver Thread (blocking read loop)             │
│   └── AnimationTimer (game loop, runs on FX thread)            │
└─────────────────────────────────────────────────────────────────┘
```

### 9.2 Concurrency Mechanisms

| Mechanism | Where Used | Purpose |
|-----------|-----------|---------|
| `ConcurrentHashMap` | GameServer (clients), GameStateManager (battles), NetworkClient (handlers) | Lock-free concurrent read/write |
| `CopyOnWriteArrayList` | BattleEngine (units, buildings) | Safe iteration during battle ticks |
| `synchronized` | ClientHandler.sendMessage(), GameServer client registration | Prevent interleaved writes |
| `ExecutorService` | GameServer (client threads), GameStateManager (battle threads) | Thread pool management |
| `ScheduledExecutorService` | GameStateManager (game tick) | Periodic task execution |
| `Platform.runLater()` | NetworkClient → UI callbacks | UI-thread safety in JavaFX |
| `volatile` | ClientHandler.running, BattleEngine.running | Thread visibility guarantee |
| `Thread (daemon)` | ResourceGeneratorThread | Background resource generation |

### 9.3 Thread Safety Patterns

#### Pattern 1: Producer-Consumer (Network Messages)
```
ClientHandler (producer) → reads messages → GameServer.processMessage()
GameServer (consumer)    → routes to services → sends responses
```

#### Pattern 2: Read-Write Lock (Game State)
```
BattleEngine tick() → writes to CopyOnWriteArrayList (safe writes during iteration)
BattleEngine broadcastState() → reads from same list (concurrent read)
```

#### Pattern 3: Event Dispatch (Client UI)
```
NetworkClient receiver thread → receives GameMessage
  → Platform.runLater() → dispatches to JavaFX Application Thread
  → Controller handler → updates UI (safe)
```

### 9.4 Potential Race Conditions & Mitigations

| Risk | Mitigation |
|------|-----------|
| Two clients writing to same ObjectOutputStream | `synchronized sendMessage()` |
| Client registration during iteration | `ConcurrentHashMap` + synchronized register/unregister |
| Battle state read during tick update | `CopyOnWriteArrayList` snapshot iteration |
| Resource update while player spending | `synchronized` blocks in PlayerService (Spring @Transactional) |
| JavaFX UI update from background thread | All UI updates via `Platform.runLater()` |

---

## 10. Database Design

### 10.1 Entity-Relationship Diagram

```
┌──────────┐     1:1      ┌──────────┐     1:N      ┌──────────────┐
│ PLAYERS  │─────────────►│  BASES   │──────────────►│  BUILDINGS   │
│          │  (cascade)   │          │ (cascade del) │              │
│ id (PK)  │              │ id (PK)  │               │ id (PK)      │
│ username │              │player_id │               │ base_id (FK) │
│ password │              │ (FK,UQ)  │               │ building_type│
│ level    │              │base_name │               │ level        │
│ gold     │              │grid_width│               │ grid_x/y     │
│ elixir   │              │defense_  │               │ hit_points   │
│ gems     │              │ power    │               │ defense_value│
│ trophies │              └──────────┘               └──────────────┘
│ is_online│
└─────┬────┘
      │
      │  1:N
      ▼
┌──────────┐
│  UNITS   │
│          │
│ id (PK)  │
│player_id │
│ (FK)     │
│ unit_type│
│ level    │
│ hit_pts  │
│ atk_power│
└──────────┘

┌──────────┐     N:1      ┌──────────┐     1:N      ┌──────────────────┐
│ PLAYERS  │◄─────────────│ BATTLES  │──────────────►│BATTLE_DEPLOYED   │
│ (attacker│  attacker_id │          │               │     _UNITS       │
│  /       │              │ id (PK)  │               │                  │
│ defender)│◄─────────────│attacker  │               │ id (PK)          │
│          │  defender_id │ _id (FK) │               │ battle_id (FK)   │
└──────────┘              │defender  │               │ unit_type        │
                          │ _id (FK) │               │ quantity         │
                          │ status   │               └──────────────────┘
                          │ stars    │
                          │ loot     │
                          │ trophies │
                          └──────────┘

┌──────────────────┐
│  CHAT_MESSAGES   │
│                  │
│ id (PK)          │
│ sender_id (FK)   │
│ channel          │
│ recipient_id     │
│ message_text     │
│ sent_at          │
└──────────────────┘
```

### 10.2 Table Specifications

| Table | Records | Access Pattern | Indexes |
|-------|---------|---------------|---------|
| `players` | Moderate (100s) | By username, by trophy range, top 20 | username, trophies DESC, is_online |
| `bases` | Same as players | By player_id (1:1) | player_id (UNIQUE) |
| `buildings` | 8-20 per base | By base_id | base_id, building_type |
| `units` | 5-30 per player | By player_id | player_id, unit_type |
| `battles` | Growing | By attacker/defender, by date | attacker_id, defender_id, status, created_at |
| `battle_deployed_units` | 3-10 per battle | By battle_id | battle_id |
| `chat_messages` | High volume | By channel, by time | channel, sent_at DESC |

### 10.3 JPA Entity Mapping

```java
// Composition: Player → Base (cascade ALL, player owns base)
@OneToOne(mappedBy = "player", cascade = CascadeType.ALL, fetch = LAZY)
private Base base;

// Composition: Base → Building (orphanRemoval, base owns buildings)
@OneToMany(mappedBy = "base", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Building> buildings;

// Aggregation: Player → Unit (no orphanRemoval)
@OneToMany(mappedBy = "player", cascade = {PERSIST, MERGE})
private List<Unit> army;

// Association: Battle → Player (no cascade)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "attacker_id")
private Player attacker;
```

---

## 11. Game Mechanics & Logic

### 11.1 Resource Economy

| Resource | Starting | Generation | Source |
|----------|---------|------------|--------|
| **Gold** | 500 | 2.0/min per Gold Mine (per level) | Gold Mine, battle loot |
| **Elixir** | 500 | 2.0/min per Elixir Collector (per level) | Elixir Collector, battle loot |
| **Gems** | 50 | Not auto-generated | Achievements, purchases (future) |

**Resource Generation**: `ResourceGeneratorThread` runs every 60 seconds:
```
gold += Σ (goldMine.productionRate × goldMine.level) × 1 minute
elixir += Σ (collector.productionRate × collector.level) × 1 minute
```

### 11.2 Building System

| Building | Size | Base HP | Defense | Production | Storage |
|----------|------|---------|---------|------------|---------|
| Town Hall | 3×3 | 500 | 0 | — | — |
| Gold Mine | 2×2 | 150 | 0 | 2.0 gold/min | — |
| Elixir Collector | 2×2 | 150 | 0 | 2.0 elixir/min | — |
| Gold Storage | 2×2 | 250 | 0 | — | 5000 |
| Elixir Storage | 2×2 | 250 | 0 | — | 5000 |
| Cannon | 2×2 | 200 | 40 | — | — |
| Archer Tower | 2×2 | 180 | 35 | — | — |
| Wall | 1×1 | 300 | 10 | — | — |
| Mortar | 2×2 | 150 | 60 | — | — |
| Barracks | 3×3 | 200 | 0 | — | — |
| Army Camp | 3×3 | 200 | 0 | — | — |
| Laboratory | 2×2 | 200 | 0 | — | — |

**Upgrade Formula** (per level):
- HP: `baseHP × 1.2^(level-1)`
- Defense: `baseDefense × 1.15^(level-1)`
- Production: `baseRate × level`

### 11.3 Unit System

| Unit | HP | Attack | Speed | Cost | Training |
|------|----|--------|-------|------|----------|
| Barbarian | 50 | 20 | 1.0 | 50 gold | 20 sec |
| Archer | 30 | 15 | 1.0 | 80 gold | 25 sec |
| Giant | 200 | 35 | 0.6 | 200 elixir | 60 sec |
| Goblin | 25 | 10 | 1.5 | 40 gold | 15 sec |
| Wizard | 80 | 50 | 0.8 | 300 elixir | 45 sec |
| Dragon | 300 | 80 | 0.8 | 500 elixir | 120 sec |
| Healer | 100 | 0 | 0.7 | 400 elixir | 90 sec |

### 11.4 Leveling System

```
XP required for level N: N × 1000
  Level 1 → 2:  1000 XP
  Level 2 → 3:  2000 XP
  Level 3 → 4:  3000 XP
  ...

XP Sources:
  - Win battle:       100-500 XP (based on stars)
  - Build building:   50 XP
  - Upgrade building: 100 XP
  - Train unit:       25 XP
```

---

## 12. Battle System

### 12.1 Battle Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│                    BATTLE LIFECYCLE                          │
│                                                             │
│ 1. MATCHMAKING                                              │
│    └─ Player requests → MatchmakingService finds opponent  │
│       (trophy range ±200, prefer offline, 30min cooldown)  │
│                                                             │
│ 2. INITIALIZATION                                           │
│    └─ BattleService creates Battle entity (status: PENDING)│
│    └─ GameStateManager creates BattleEngine thread          │
│    └─ Defender's base buildings copied into battle arena    │
│                                                             │
│ 3. DEPLOYMENT PHASE                                         │
│    └─ Attacker deploys units at grid edges (x=0 or x=19)  │
│    └─ Each DEPLOY_UNIT message → BattleEngine.deployUnit() │
│                                                             │
│ 4. SIMULATION (20 ticks/sec, max 3 minutes)                │
│    └─ Per tick:                                             │
│       ├── Unit AI: find target → move → attack             │
│       ├── Defense AI: buildings fire at nearby units         │
│       ├── Cleanup: remove destroyed entities                │
│       └── Broadcast: send state to both players             │
│                                                             │
│ 5. RESOLUTION                                               │
│    └─ Trigger: all buildings destroyed OR time up           │
│    └─ Calculate: destruction%, stars, loot, trophies        │
│    └─ Apply rewards to attacker/defender                    │
│    └─ Status → COMPLETED, broadcast BATTLE_END              │
└─────────────────────────────────────────────────────────────┘
```

### 12.2 Star Rating System

| Stars | Condition |
|-------|----------|
| ★☆☆ | Destruction ≥ 50% |
| ★★☆ | Destruction ≥ 75% |
| ★★★ | Destruction = 100% (all buildings destroyed) |
| ☆☆☆ | Destruction < 50% (attacker loses) |

### 12.3 Loot Calculation

```
Gold looted  = defender.gold  × (destruction% / 100) × 0.20  (max 20% of defender's gold)
Elixir looted = defender.elixir × (destruction% / 100) × 0.20

Trophies won  = 10 + (stars × 10)   → Range: 10-40
Trophies lost = same amount from defender
```

### 12.4 Unit AI Algorithm

```
for each alive unit:
    if no target or target destroyed:
        target = findNearestBuilding(unit.position, aliveBuildings)

    if target exists:
        distance = distance(unit.position, target.position)
        if distance <= ATTACK_RANGE (1.5):
            unit.attackBuilding(target)     # Apply unit.attackPower damage
        else:
            unit.moveToward(target.position) # Move by movementSpeed units
```

### 12.5 Defense AI Algorithm

```
for each non-destroyed defensive building (CANNON, ARCHER_TOWER, MORTAR):
    nearestUnit = findNearestUnit(building.position, aliveUnits)
    if nearestUnit exists and distance <= DEFENSE_RANGE (5.0):
        nearestUnit.takeDamage(building.defenseValue)
```

---

## 13. UI Layout Design

### 13.1 Login Screen
```
┌──────────────────────────────────────┐
│                                      │
│         ⚔ CLASH OF CLANS MINI       │
│         Multiplayer Strategy Game     │
│                                      │
│     ┌──────────────────────────┐     │
│     │  Username                │     │
│     └──────────────────────────┘     │
│     ┌──────────────────────────┐     │
│     │  Password    ●●●●●●●    │     │
│     └──────────────────────────┘     │
│                                      │
│     ┌──────────┐  ┌──────────┐      │
│     │  LOGIN   │  │ REGISTER │      │
│     └──────────┘  └──────────┘      │
│                                      │
│          Status message...           │
└──────────────────────────────────────┘
```

### 13.2 Dashboard Screen
```
┌──────────────────────────────────────────────┐
│  🪙 Gold: 5,000  ⚗ Elixir: 4,200  💎 Gems: 100  │
│  🏆 Trophies: 450   ⭐ Level: 5                │
├──────────────────────────────────────────────┤
│                                              │
│         Welcome, DragonSlayer!                │
│                                              │
│    ┌──────────────┐  ┌──────────────┐       │
│    │  🏰 MY BASE  │  │  ⚔ ATTACK   │       │
│    └──────────────┘  └──────────────┘       │
│                                              │
│    ┌──────────────┐  ┌──────────────┐       │
│    │  🏋 TRAIN    │  │  🏆 LEADERS  │       │
│    └──────────────┘  └──────────────┘       │
│                                              │
│            [ 🚪 LOGOUT ]                    │
│                                              │
└──────────────────────────────────────────────┘
```

### 13.3 Base Builder Screen
```
┌──────────────────────────────────────────────────────────────┐
│ ◄ Back              Base Builder           Defense: 350      │
├────────────────────────────────────┬─────────────────────────┤
│                                    │  Buildings:             │
│   ┌──┬──┬──┬──┬──┬──┬──┬──┬──┐   │                         │
│   │  │GM│  │  │  │  │  │  │  │   │  [Town Hall]  Lv.3      │
│   ├──┼──┼──┤  │  │  │  │  │  │   │  [Gold Mine]  Lv.3      │
│   │  │  │  │  │  │  │CN│  │  │   │  [Cannon]     Lv.3      │
│   ├──┤  │  ├──┼──┤  │  │  │  │   │  [Archer Tower] Lv.2    │
│   │  │  │  │  │TH│  │  │  │  │   │                         │
│   │  │  │  │  │  │  │  │AT│  │   │  Place Building:        │
│   ├──┤  │  ├──┼──┤  │  │  │  │   │  ┌─────────────────┐   │
│   │  │EC│  │  │  │  │  │  │  │   │  │ [Select Type ▼] │   │
│   ├──┼──┤  │  │  │  │  │  │  │   │  └─────────────────┘   │
│   │  │  │BK│  │  │AC│  │  │  │   │  [ PLACE ]  [ UPGRADE ]│
│   └──┴──┴──┴──┴──┴──┴──┴──┴──┘   │  [ REMOVE ]            │
│     20 × 20 Canvas (600 × 600)    │                         │
└────────────────────────────────────┴─────────────────────────┘
```

### 13.4 Battle Screen
```
┌─────────────────────────────────────────────────────────────────┐
│  ⭐☆☆  │  Destruction: 32.5%  │  Time: 2:15  │  [ SURRENDER ] │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌──────────────────────────────────────────────────────┐      │
│   │                                                      │      │
│   │              BATTLE ARENA                            │      │
│   │         (Real-time Canvas Rendering)                 │      │
│   │                                                      │      │
│   │   🗡️ Barbarian units moving toward buildings         │      │
│   │   🏰 Defender buildings firing at units              │      │
│   │   💥 Explosions and damage indicators                │      │
│   │                                                      │      │
│   │              700 × 500 Canvas                        │      │
│   └──────────────────────────────────────────────────────┘      │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│   Deploy Units:                                                 │
│   [ 🗡️ Barbarian ×5 ] [ 🏹 Archer ×3 ] [ 🛡️ Giant ×2 ]       │
│   [ 🐲 Dragon ×1 ]    [ 🧙 Wizard ×0 ]                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 14. Advanced Features

### 14.1 Matchmaking Algorithm

```java
public Player findOpponent(Player attacker) {
    int range = 200;
    int minTrophies = attacker.getTrophies() - range;
    int maxTrophies = attacker.getTrophies() + range;

    List<Player> candidates = playerRepository
        .findByTrophiesBetween(minTrophies, maxTrophies);

    // Filter: not self, not recently attacked (30min cooldown), prefer offline
    candidates = candidates.stream()
        .filter(p -> !p.getId().equals(attacker.getId()))
        .filter(p -> !recentlyAttacked(attacker, p))
        .sorted(Comparator.comparing(Player::isOnline))  // offline first
        .collect(Collectors.toList());

    // Random selection from top candidates
    if (!candidates.isEmpty()) {
        return candidates.get(random.nextInt(Math.min(5, candidates.size())));
    }
    return null;
}
```

### 14.2 Resource Production Pipeline

```
ResourceGeneratorThread (daemon thread, 60-second interval)
  │
  ├── Load all players from database
  ├── For each player:
  │     ├── Calculate gold production: Σ goldMine.productionRate × level
  │     ├── Calculate elixir production: Σ collector.productionRate × level
  │     ├── Add to player's resources (capped by storage capacity)
  │     └── If player online → send RESOURCE_UPDATE via GameServer
  └── Save all updated players
```

### 14.3 Building Placement Validation

```java
public boolean canPlaceBuilding(int gridX, int gridY, int width, int height) {
    // Boundary check
    if (gridX < 0 || gridY < 0 ||
        gridX + width > gridWidth || gridY + height > gridHeight) {
        return false;
    }

    // Overlap check against all existing buildings
    for (Building existing : buildings) {
        boolean overlapsX = gridX < existing.getGridX() + existing.getWidth()
                         && gridX + width > existing.getGridX();
        boolean overlapsY = gridY < existing.getGridY() + existing.getHeight()
                         && gridY + height > existing.getGridY();
        if (overlapsX && overlapsY) return false;
    }
    return true;
}
```

### 14.4 Password Security

```java
// Registration: hash with BCrypt (cost factor 10)
String hash = passwordEncoder.encode(rawPassword);
player.setPasswordHash(hash);

// Login: verify against stored hash
boolean valid = passwordEncoder.matches(rawPassword, player.getPasswordHash());
```

### 14.5 Concurrent Battle Management

```java
public class GameStateManager {
    private final ConcurrentHashMap<String, BattleEngine> activeBattles;
    private final ExecutorService battleThreadPool;

    public String startBattle(Battle battle, List<Building> defenderBuildings) {
        String battleId = UUID.randomUUID().toString();
        BattleEngine engine = new BattleEngine(battle, defenderBuildings, callback);
        activeBattles.put(battleId, engine);
        battleThreadPool.submit(engine);  // Run in dedicated thread
        return battleId;
    }
}
```

---

## 15. Final Project Summary & How to Run

### 15.1 Project Statistics

| Metric | Count |
|--------|-------|
| Total Java files | 30 |
| Server classes | 18 |
| Client classes | 12 |
| FXML layouts | 6 |
| CSS stylesheets | 1 |
| SQL schema files | 1 |
| Database tables | 7 |
| Database views | 2 |
| Message types | 25+ |
| Building types | 12 |
| Unit types | 7 |
| REST endpoints | 5 |

### 15.2 Prerequisites

1. **Java 17+** (JDK, not JRE)
2. **Maven 3.8+**
3. **MySQL 8.0** or **PostgreSQL 16**
4. **JavaFX SDK 17** (included via Maven dependency)

### 15.3 Database Setup

```bash
# 1. Create database
mysql -u root -p
CREATE DATABASE clash_game_db;
EXIT;

# 2. Import schema (optional — JPA can auto-generate tables)
mysql -u root -p clash_game_db < database/schema.sql
```

### 15.4 Running the Server

```bash
cd server

# Configure database in src/main/resources/application.properties:
#   spring.datasource.url=jdbc:mysql://localhost:3306/clash_game_db
#   spring.datasource.username=root
#   spring.datasource.password=your_password

mvn clean install
mvn spring-boot:run
```

The server starts:
- **REST API** on `http://localhost:8080`
- **Game Socket** on `localhost:9090`

### 15.5 Running the Client

```bash
cd client

mvn clean install
mvn javafx:run
```

### 15.6 Gameplay Flow

1. **Register** a new account on the login screen
2. **Login** to see the dashboard with your resources
3. **Build your base**: Place buildings on the 20×20 grid
4. **Train army**: Recruit Barbarians, Archers, Giants, Dragons, etc.
5. **Find opponent**: Matchmaking finds a player near your trophy level
6. **Attack**: Deploy units on the battle grid, watch real-time combat
7. **Collect rewards**: Gold, Elixir, Trophies based on destruction percentage
8. **Climb the Leaderboard**: Compete against other players for top rankings

### 15.7 Architecture Recap

```
┌─────────────────────────────────────────────────────────┐
│              ADVANCED OOP CONCEPTS USED                  │
├─────────────────────────────────────────────────────────┤
│ ✓ Encapsulation    — Private fields, validation methods │
│ ✓ Inheritance      — Enum hierarchies, Runnable impl    │
│ ✓ Polymorphism     — Message dispatch, type-based init  │
│ ✓ Abstraction      — Repositories, NetworkClient facade │
│ ✓ Composition      — Player→Base→Buildings              │
│ ✓ Aggregation      — Player→Units, Battle→UnitTypes     │
│ ✓ MVC Pattern      — FXML + Controllers + Models        │
│ ✓ Singleton        — GameEngine.getInstance()           │
│ ✓ Observer         — JavaFX properties, msg handlers    │
│ ✓ Factory Method   — GameMessage.createXxx()            │
│ ✓ Strategy         — Battle tick phases                 │
│ ✓ Repository       — Spring Data JPA                    │
│ ✓ Multithreading   — 5+ thread types, pools, daemon     │
│ ✓ Thread Safety    — ConcurrentHashMap, synchronized    │
│ ✓ Networking       — TCP sockets + REST API             │
│ ✓ Serialization    — ObjectStreams + JSON                │
│ ✓ Database Design  — 7 tables, ER relationships, views  │
│ ✓ Security         — BCrypt password hashing            │
│ ✓ UI/UX            — Canvas rendering, CSS theming      │
└─────────────────────────────────────────────────────────┘
```

---

*This project was designed as a comprehensive Advanced OOP university project demonstrating mastery of Java, object-oriented design, client-server architecture, multithreading, database design, and JavaFX UI development.*
