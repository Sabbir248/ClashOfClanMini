-- ═══════════════════════════════════════════════════════════════════════
--  Clash of Clans Mini — Database Schema (MySQL / PostgreSQL Compatible)
-- ═══════════════════════════════════════════════════════════════════════
--  ER Relationships:
--    Player ──1:1── Base (composition)
--    Base   ──1:N── Building (composition, cascade delete)
--    Player ──1:N── Unit (aggregation)
--    Player ──1:N── Battle (as attacker)
--    Player ──1:N── Battle (as defender)
--    Battle ──M:N── UnitType (deployed units)
-- ═══════════════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. Players Table
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS players (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)     NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    level           INT             NOT NULL DEFAULT 1,
    xp              INT             NOT NULL DEFAULT 0,
    gold            INT             NOT NULL DEFAULT 500,
    elixir          INT             NOT NULL DEFAULT 500,
    gems            INT             NOT NULL DEFAULT 50,
    trophies        INT             NOT NULL DEFAULT 0,
    clan_name       VARCHAR(100)    DEFAULT NULL,
    is_online       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login      TIMESTAMP       NULL,

    -- Indexes for frequent queries
    INDEX idx_players_trophies (trophies DESC),
    INDEX idx_players_username (username),
    INDEX idx_players_online   (is_online)
);

-- ────────────────────────────────────────
-- 1b. Player Troop Levels (Laboratory upgrades)
-- ────────────────────────────────────────
--    Tracks the global upgrade level for each troop type per player.
--    Upgraded via the Laboratory building.
ALTER TABLE players
    ADD COLUMN lab_upgrading_troop VARCHAR(20) DEFAULT NULL,
    ADD COLUMN lab_upgrade_time_remaining INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS player_troop_levels (
    player_id       BIGINT          NOT NULL,
    unit_type       VARCHAR(20)     NOT NULL,
    troop_level     INT             NOT NULL DEFAULT 1,

    PRIMARY KEY (player_id, unit_type),
    CONSTRAINT fk_ptl_player
        FOREIGN KEY (player_id) REFERENCES players(id)
        ON DELETE CASCADE,

    INDEX idx_ptl_player (player_id)
);


-- ────────────────────────────────────────
-- 2. Bases Table (1:1 with Player)
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS bases (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    player_id       BIGINT          NOT NULL UNIQUE,
    base_name       VARCHAR(100)    NOT NULL DEFAULT 'Home Village',
    grid_width      INT             NOT NULL DEFAULT 20,
    grid_height     INT             NOT NULL DEFAULT 20,
    defense_power   INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_bases_player
        FOREIGN KEY (player_id) REFERENCES players(id)
        ON DELETE CASCADE
);


-- ────────────────────────────────────────
-- 3. Buildings Table (N:1 with Base)
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS buildings (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    base_id         BIGINT          NOT NULL,
    building_type   VARCHAR(30)     NOT NULL,       -- Enum: TOWN_HALL, GOLD_MINE, CANNON, etc.
    level           INT             NOT NULL DEFAULT 1,
    grid_x          INT             NOT NULL,
    grid_y          INT             NOT NULL,
    width           INT             NOT NULL DEFAULT 2,
    height          INT             NOT NULL DEFAULT 2,
    hit_points      INT             NOT NULL DEFAULT 100,
    max_hit_points  INT             NOT NULL DEFAULT 100,
    defense_value   INT             NOT NULL DEFAULT 0,
    production_rate DOUBLE          NOT NULL DEFAULT 0.0,
    storage_capacity INT            NOT NULL DEFAULT 0,
    is_upgrading    BOOLEAN         NOT NULL DEFAULT FALSE,
    upgrade_time_remaining INT     NOT NULL DEFAULT 0,

    CONSTRAINT fk_buildings_base
        FOREIGN KEY (base_id) REFERENCES bases(id)
        ON DELETE CASCADE,

    -- Prevent overlapping buildings (application layer enforces grid overlap,
    -- but we ensure no duplicate position per base)
    INDEX idx_buildings_base (base_id),
    INDEX idx_buildings_type (building_type)
);


-- ────────────────────────────────────────
-- 4. Units Table (N:1 with Player)
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS units (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    player_id       BIGINT          NOT NULL,
    unit_type       VARCHAR(20)     NOT NULL,       -- Enum: BARBARIAN, ARCHER, GIANT, etc.
    level           INT             NOT NULL DEFAULT 1,
    hit_points      INT             NOT NULL DEFAULT 50,
    max_hit_points  INT             NOT NULL DEFAULT 50,
    attack_power    INT             NOT NULL DEFAULT 20,
    movement_speed  DOUBLE          NOT NULL DEFAULT 1.0,
    is_alive        BOOLEAN         NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_units_player
        FOREIGN KEY (player_id) REFERENCES players(id)
        ON DELETE CASCADE,

    INDEX idx_units_player (player_id),
    INDEX idx_units_type   (unit_type)
);


-- ────────────────────────────────────────
-- 5. Battles Table (N:1 with attacker, N:1 with defender)
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS battles (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    attacker_id     BIGINT          NOT NULL,
    defender_id     BIGINT          NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',   -- PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    stars_earned    INT             NOT NULL DEFAULT 0,
    destruction_pct DOUBLE          NOT NULL DEFAULT 0.0,
    gold_looted     INT             NOT NULL DEFAULT 0,
    elixir_looted   INT             NOT NULL DEFAULT 0,
    trophies_won    INT             NOT NULL DEFAULT 0,
    trophies_lost   INT             NOT NULL DEFAULT 0,
    attacker_units_deployed INT NOT NULL DEFAULT 0,
    attacker_units_lost     INT NOT NULL DEFAULT 0,
    buildings_destroyed     INT NOT NULL DEFAULT 0,
    total_buildings         INT NOT NULL DEFAULT 0,
    battle_duration         INT NOT NULL DEFAULT 0,
    max_duration            INT NOT NULL DEFAULT 180,
    started_at      TIMESTAMP       NULL,
    ended_at        TIMESTAMP       NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_battles_attacker
        FOREIGN KEY (attacker_id) REFERENCES players(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_battles_defender
        FOREIGN KEY (defender_id) REFERENCES players(id)
        ON DELETE CASCADE,

    -- Cannot attack yourself
    CONSTRAINT chk_no_self_attack
        CHECK (attacker_id <> defender_id),

    INDEX idx_battles_attacker (attacker_id),
    INDEX idx_battles_defender (defender_id),
    INDEX idx_battles_status   (status),
    INDEX idx_battles_date     (created_at DESC)
);


-- ────────────────────────────────────────
-- 6. Battle Deployed Units (M:N junction)
-- ────────────────────────────────────────
--    Tracks which unit types were deployed in each battle
CREATE TABLE IF NOT EXISTS battle_deployed_units (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    battle_id       BIGINT          NOT NULL,
    unit_type       VARCHAR(20)     NOT NULL,
    quantity        INT             NOT NULL DEFAULT 1,

    CONSTRAINT fk_bdu_battle
        FOREIGN KEY (battle_id) REFERENCES battles(id)
        ON DELETE CASCADE,

    INDEX idx_bdu_battle (battle_id)
);


-- ────────────────────────────────────────
-- 7. Chat Messages Table (Global / Clan Chat)
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS chat_messages (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    sender_id       BIGINT          NOT NULL,
    channel         VARCHAR(50)     NOT NULL DEFAULT 'GLOBAL',   -- GLOBAL, CLAN, WHISPER
    recipient_id    BIGINT          NULL,                         -- For WHISPER messages
    message_text    TEXT            NOT NULL,
    sent_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_chat_sender
        FOREIGN KEY (sender_id) REFERENCES players(id)
        ON DELETE CASCADE,

    INDEX idx_chat_channel (channel),
    INDEX idx_chat_time    (sent_at DESC)
);


-- ═══════════════════════════════════════════════════════════════════════
--  VIEWS — Useful aggregated queries
-- ═══════════════════════════════════════════════════════════════════════

-- Leaderboard View (top players by trophies)
CREATE OR REPLACE VIEW v_leaderboard AS
SELECT
    p.id,
    p.username,
    p.level,
    p.trophies,
    p.clan_name,
    COUNT(DISTINCT b_wins.id) AS total_wins,
    COUNT(DISTINCT b_all.id)  AS total_battles
FROM players p
LEFT JOIN battles b_wins ON b_wins.attacker_id = p.id
    AND b_wins.status = 'COMPLETED' AND b_wins.stars_earned >= 1
LEFT JOIN battles b_all  ON (b_all.attacker_id = p.id OR b_all.defender_id = p.id)
    AND b_all.status = 'COMPLETED'
GROUP BY p.id, p.username, p.level, p.trophies, p.clan_name
ORDER BY p.trophies DESC;

-- Player Overview (resources + base stats)
CREATE OR REPLACE VIEW v_player_overview AS
SELECT
    p.id,
    p.username,
    p.level,
    p.gold,
    p.elixir,
    p.gems,
    p.trophies,
    p.is_online,
    b.defense_power,
    COUNT(bldg.id)  AS building_count,
    COUNT(u.id)     AS army_size
FROM players p
LEFT JOIN bases b       ON b.player_id = p.id
LEFT JOIN buildings bldg ON bldg.base_id = b.id
LEFT JOIN units u       ON u.player_id = p.id AND u.is_alive = TRUE
GROUP BY p.id, p.username, p.level, p.gold, p.elixir,
         p.gems, p.trophies, p.is_online, b.defense_power;


-- ═══════════════════════════════════════════════════════════════════════
--  SAMPLE DATA — Starter records for testing
-- ═══════════════════════════════════════════════════════════════════════

-- Sample Players (passwords are BCrypt hashes of "password123")
INSERT INTO players (username, password_hash, level, xp, gold, elixir, gems, trophies)
VALUES
    ('DragonSlayer',  '$2a$10$dXJ3SW6G7P50lGmMQgel3uGqnRseOhFWjlChzB8T.3FQH9hRqB.uG',
     5, 2400, 5000, 4200, 100, 450),
    ('WarChief',      '$2a$10$dXJ3SW6G7P50lGmMQgel3uGqnRseOhFWjlChzB8T.3FQH9hRqB.uG',
     4, 1800, 3500, 3000, 75, 380),
    ('GoblinKing',    '$2a$10$dXJ3SW6G7P50lGmMQgel3uGqnRseOhFWjlChzB8T.3FQH9hRqB.uG',
     3, 1200, 2200, 2000, 50, 220),
    ('ArcherQueen',   '$2a$10$dXJ3SW6G7P50lGmMQgel3uGqnRseOhFWjlChzB8T.3FQH9hRqB.uG',
     6, 3500, 7000, 6500, 150, 600),
    ('WallBreaker99', '$2a$10$dXJ3SW6G7P50lGmMQgel3uGqnRseOhFWjlChzB8T.3FQH9hRqB.uG',
     2, 600, 1500, 1200, 30, 120);

-- Sample Bases
INSERT INTO bases (player_id, base_name, defense_power)
VALUES
    (1, 'Dragon Lair',    350),
    (2, 'War Camp',       280),
    (3, 'Goblin Hideout', 150),
    (4, 'Queen''s Keep',  500),
    (5, 'Demolition HQ',  90);

-- Sample Buildings for Player 1 (DragonSlayer)
INSERT INTO buildings (base_id, building_type, level, grid_x, grid_y, width, height,
                       hit_points, max_hit_points, defense_value, production_rate, storage_capacity)
VALUES
    (1, 'TOWN_HALL',         3, 9, 9, 3, 3, 600, 600, 0, 0.0, 0),
    (1, 'GOLD_MINE',         3, 2, 2, 2, 2, 200, 200, 0, 3.6, 0),
    (1, 'ELIXIR_COLLECTOR',  3, 2, 6, 2, 2, 200, 200, 0, 3.6, 0),
    (1, 'GOLD_STORAGE',      2, 6, 2, 2, 2, 300, 300, 0, 0.0, 5000),
    (1, 'CANNON',            3, 14, 4, 2, 2, 250, 250, 50, 0.0, 0),
    (1, 'ARCHER_TOWER',      2, 14, 10, 2, 2, 200, 200, 40, 0.0, 0),
    (1, 'BARRACKS',          2, 6, 14, 3, 3, 250, 250, 0, 0.0, 0),
    (1, 'ARMY_CAMP',         2, 12, 14, 3, 3, 200, 200, 0, 0.0, 0);

-- Sample Units for Player 1
INSERT INTO units (player_id, unit_type, level, hit_points, max_hit_points, attack_power, movement_speed)
VALUES
    (1, 'BARBARIAN', 2, 65, 65, 24, 1.0),
    (1, 'BARBARIAN', 2, 65, 65, 24, 1.0),
    (1, 'ARCHER',    2, 36, 36, 18, 1.0),
    (1, 'ARCHER',    2, 36, 36, 18, 1.0),
    (1, 'GIANT',     1, 200, 200, 35, 0.6),
    (1, 'DRAGON',    1, 300, 300, 80, 0.8);

-- Sample Battle Record
INSERT INTO battles (attacker_id, defender_id, status, stars_earned, destruction_pct,
                     gold_looted, elixir_looted, trophies_won, trophies_lost,
                     started_at, ended_at)
VALUES
    (1, 3, 'COMPLETED', 3, 100.0, 440, 400, 30, 30,
     '2024-01-15 14:30:00', '2024-01-15 14:33:00'),
    (2, 5, 'COMPLETED', 2, 78.5, 300, 240, 25, 25,
     '2024-01-15 15:00:00', '2024-01-15 15:02:30'),
    (4, 1, 'COMPLETED', 1, 52.0, 1000, 840, 20, 20,
     '2024-01-16 10:00:00', '2024-01-16 10:03:00');

INSERT INTO battle_deployed_units (battle_id, unit_type, quantity)
VALUES
    (1, 'BARBARIAN', 5),
    (1, 'ARCHER', 3),
    (1, 'GIANT', 2),
    (2, 'BARBARIAN', 8),
    (2, 'GOBLIN', 4),
    (3, 'DRAGON', 2),
    (3, 'WIZARD', 3),
    (3, 'HEALER', 1);
