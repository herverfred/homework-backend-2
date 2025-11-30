-- ============================================
-- Mission Center Service - Database Schema
-- Final Version
-- ============================================

-- Set connection charset to UTF-8
SET NAMES utf8mb4;

-- 1. Users Table
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'User ID',
    username VARCHAR(50) NOT NULL COMMENT 'Username',
    password VARCHAR(255) NULL COMMENT 'Password',
    points INT NOT NULL DEFAULT 0 COMMENT 'User points',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Registration time',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',

    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Users table';

-- ============================================

-- 2. Games Table
CREATE TABLE games (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Game ID',
    name VARCHAR(100) NOT NULL COMMENT 'Game name',
    description VARCHAR(500) NULL COMMENT 'Game description',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',

    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Games table';

-- ============================================

-- 3. Game Play Records Table
CREATE TABLE games_play_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Record ID',
    event_id VARCHAR(64) NOT NULL COMMENT 'Event ID for idempotency',
    user_id BIGINT NOT NULL COMMENT 'User ID',
    game_id BIGINT NOT NULL COMMENT 'Game ID',
    score INT NOT NULL DEFAULT 0 COMMENT 'Score for this game session',
    played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Play time',

    UNIQUE KEY uk_event_id (event_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Game play records table';

-- ============================================

-- 4. Missions Table
CREATE TABLE missions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Mission ID',
    user_id BIGINT NOT NULL COMMENT 'User ID',
    mission_type VARCHAR(50) NOT NULL COMMENT 'Mission type: LOGIN_CONSECUTIVE, LAUNCH_GAMES, PLAY_SCORE',
    cycle_start_date DATE NOT NULL COMMENT 'Start date of 30-day cycle',
    is_completed BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Is completed',
    completed_at TIMESTAMP NULL COMMENT 'Completion time',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',

    UNIQUE KEY uk_user_mission_cycle (user_id, mission_type, cycle_start_date),
    INDEX idx_user_cycle (user_id, cycle_start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User missions table';

-- ============================================

-- 5. User Login Records Table
CREATE TABLE user_login_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Record ID',
    user_id BIGINT NOT NULL COMMENT 'User ID',
    login_date DATE NOT NULL COMMENT 'Login date',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',

    UNIQUE KEY uk_user_date (user_id, login_date),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User login records table';

-- ============================================

-- 6. User Game Launches Table
CREATE TABLE user_game_launches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Record ID',
    user_id BIGINT NOT NULL COMMENT 'User ID',
    game_id BIGINT NOT NULL COMMENT 'Game ID',
    launch_date DATE NOT NULL COMMENT 'Launch date (date only)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',

    UNIQUE KEY uk_user_game_date (user_id, game_id, launch_date),
    INDEX idx_user_id (user_id),
    INDEX idx_launch_date (launch_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User game launches table';

-- ============================================

-- 7. Mission Rewards Table (for idempotency guarantee)
CREATE TABLE mission_rewards (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Record ID',
    user_id BIGINT NOT NULL COMMENT 'User ID',
    reward_type VARCHAR(50) NOT NULL COMMENT 'Reward type: MISSION_30_DAY',
    reward_period VARCHAR(20) NOT NULL COMMENT 'Reward period: 2025-01, 2025-02',
    points INT NOT NULL COMMENT 'Reward points',
    distributed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Distribution time',

    UNIQUE KEY uk_user_reward_period (user_id, reward_type, reward_period),
    INDEX idx_user_id (user_id),
    INDEX idx_period (reward_period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Mission rewards table';

-- ============================================

-- 8. Message Outbox Table (for failed message compensation)
CREATE TABLE message_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Record ID',
    event_id VARCHAR(64) NOT NULL COMMENT 'Unique event ID',
    topic VARCHAR(100) NOT NULL COMMENT 'RocketMQ topic',
    payload TEXT NOT NULL COMMENT 'JSON serialized message content',
    event_type VARCHAR(50) NOT NULL COMMENT 'Event type: LOGIN, GAME_LAUNCH, GAME_PLAY, MISSION_COMPLETED',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Status: PENDING / FAILED',
    retry_count INT NOT NULL DEFAULT 0 COMMENT 'Retry count',
    max_retries INT NOT NULL DEFAULT 10 COMMENT 'Max retry attempts',
    next_retry_at TIMESTAMP NOT NULL COMMENT 'Next retry time',
    error_message VARCHAR(500) NULL COMMENT 'Last error message',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',

    UNIQUE KEY uk_event_id (event_id),
    INDEX idx_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Message outbox for failed message compensation';

-- ============================================
-- Initialize Test Data
-- ============================================

-- Insert test games
INSERT INTO games (name, description) VALUES
('Pharaoh Treasure', 'Egyptian themed slot game'),
('Ocean Quest', 'Ocean adventure slot game'),
('Nordic Gods', 'Norse mythology slot game'),
('Lucky 777', 'Classic slot machine game'),
('Magic Forest', 'Fantasy magic slot game');

-- Insert test users
INSERT INTO users (username, password, points) VALUES
('test_user', 'password123', 0),
('test_user1', 'password123', 0),
('test_user2', 'password123', 0),
('test_user3', 'password123', 0),
('test_user4', 'password123', 0);

-- Note: missions are not initialized here, they are automatically created on first API call
