# Mission Center Service - User Guide

A Spring Boot backend service implementing a 30-day mission system for new users with MySQL, Redis, and RocketMQ.

---

## Table of Contents

- [Postman Collection](#postman-collection)
- [Project Structure](#project-structure)
- [Quick Start](#quick-start)
- [API Documentation](#api-documentation)
  - [POST /api/login](#1-user-login---post-apilogin)
  - [POST /api/launchGame](#2-launch-game---post-apilaunchgame)
  - [POST /api/play](#3-play-game---post-apiplay)
  - [GET /api/missions](#4-get-mission-progress---get-apimissions)
  - [GET /api/rewards](#5-get-reward-history---get-apirewards)
  - [GET /api/games](#6-get-games-list---get-apigames)
- [Database Schema](#database-schema)
- [Test Examples](#test-examples)

---

## curl Scripts

Quick API testing scripts are available in the `curl/` directory:

```
curl/
├── 01-login.sh              # User login API
├── 02-get-games.sh          # Get games list
├── 03-launch-game.sh        # Launch game API
├── 04-play-game.sh          # Play game API
├── 05-get-missions.sh       # Get mission progress
├── 06-get-rewards.sh        # Get reward history
├── test-all-missions.sh     # Complete mission flow test
└── reset-db.sh              # Reset database records
```

**Usage:**
```bash
# Make scripts executable
chmod +x curl/*.sh

# Run individual API test
./curl/01-login.sh

# Run complete mission flow test
./curl/test-all-missions.sh

# Reset database for fresh testing
./curl/reset-db.sh
```

---

## Postman Collection

Import the Postman collection and environment for quick API testing:

```
postman/
└── Mission-Center-API.postman_collection.json    # API Collection
```

**Import Steps:**
1. Open Postman
2. Click **Import** button
3. Drag and drop both JSON files
4. Select **Mission-Center-Local** environment from the top-right dropdown

---

## Project Structure

```
├── src/main/java/com/example/demo/
│   ├── controller/          # REST API endpoints
│   ├── service/             # Business logic
│   ├── repository/          # Data access (JPA)
│   ├── entity/              # Database entities
│   ├── dto/                 # Request/Response DTOs
│   ├── event/               # RocketMQ event classes
│   ├── consumer/            # RocketMQ message consumers
│   ├── config/              # Configuration classes
│   └── exception/           # Exception handlers
├── src/main/resources/
│   └── application.yaml     # Application config
├── postman/                 # Postman collection
├── init.sql                 # Database initialization
└── docker-compose.yaml      # Infrastructure services
```

---

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 21+
- Maven

### 1. Start Infrastructure Services

```bash
docker-compose up -d
```

This starts:
| Service | Port | Description |
|---------|------|-------------|
| MySQL | 3306 | Database |
| Redis | 6379 | Cache & Distributed Lock |
| RocketMQ NameServer | 9876 | Message Queue |
| RocketMQ Broker | 10911 | Message Broker |
| RocketMQ Console | 8088 | Web UI |

### 2. Wait for Services (about 30 seconds)

```bash
docker-compose ps
```

### 3. Start Application

```bash
./mvnw spring-boot:run
```

Application runs at: `http://localhost:8080`

### 4. Stop Services

```bash
docker-compose down
```

---

## API Documentation

### 1. User Login - POST `/api/login`

Authenticate user and trigger login event for mission tracking.

**Request**
```json
{
  "username": "test_user",
  "password": "password123",
  "loginDate": "2025-11-19"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| username | String | Yes | Username |
| password | String | Yes | Password |
| loginDate | String | No | Login date (yyyy-MM-dd), defaults to today |

**Success Response (200)**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "userId": 1,
    "username": "test_user",
    "points": 0
  }
}
```

**Error Response (401)**
```json
{
  "success": false,
  "message": "Invalid username or password"
}
```

---

### 2. Launch Game - POST `/api/launchGame`

Record game launch event for mission tracking.

**Request**
```json
{
  "userId": 1,
  "gameId": 1
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| userId | Long | Yes | User ID |
| gameId | Long | Yes | Game ID (1-5) |

**Success Response (200)**
```json
{
  "success": true,
  "message": "Game launch recorded successfully",
  "data": "User 1 launched game 1"
}
```

**Error Response (404)**
```json
{
  "success": false,
  "message": "User with id 999 not found"
}
```

---

### 3. Play Game - POST `/api/play`

Record gameplay session. Score is randomly generated (0-1000) by the server.

**Request**
```json
{
  "userId": 1,
  "gameId": 2
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| userId | Long | Yes | User ID |
| gameId | Long | Yes | Game ID (1-5) |

**Success Response (200)**
```json
{
  "success": true,
  "message": "Game play recorded successfully",
  "data": {
    "userId": 1,
    "gameId": 2,
    "score": 742
  }
}
```

---

### 4. Get Mission Progress - GET `/api/missions`

Get user's mission progress for all three missions.

**Request**
```
GET /api/missions?userId=1
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| userId | Long | Yes | User ID |

**Success Response (200)**
```json
{
  "success": true,
  "message": "Mission progress retrieved successfully",
  "data": {
    "userId": 1,
    "loginMission": {
      "type": "CONSECUTIVE_LOGIN_3_DAYS",
      "description": "Log in for 3 consecutive days",
      "currentProgress": 2,
      "targetProgress": 3,
      "progressText": "2 / 3 days",
      "completed": false,
      "completedAt": null,
      "extraInfo": {
        "consecutiveDays": 2
      }
    },
    "launchMission": {
      "type": "LAUNCH_3_DIFFERENT_GAMES",
      "description": "Launch at least 3 different games",
      "currentProgress": 1,
      "targetProgress": 3,
      "progressText": "1 / 3 games",
      "completed": false,
      "completedAt": null,
      "extraInfo": {
        "uniqueGamesLaunched": 1
      }
    },
    "playMission": {
      "type": "PLAY_3_GAMES_TOTAL_SCORE_1000",
      "description": "Play at least 3 game sessions with combined score > 1,000 points",
      "currentProgress": 0,
      "targetProgress": 3,
      "progressText": "0 plays, 0 / 1,000 points",
      "completed": false,
      "completedAt": null,
      "extraInfo": {
        "playCount": 0,
        "totalScore": 0
      }
    }
  }
}
```

**Mission Types**

| Mission | Completion Condition |
|---------|---------------------|
| CONSECUTIVE_LOGIN_3_DAYS | Log in for 3 consecutive days |
| LAUNCH_3_DIFFERENT_GAMES | Launch at least 3 different games |
| PLAY_3_GAMES_TOTAL_SCORE_1000 | Play 3+ sessions with total score > 1000 |

**Reward**: When all 3 missions are completed, user receives **777 points**.

---

### 5. Get Reward History - GET `/api/rewards`

Get user's reward distribution history.

**Request**
```
GET /api/rewards?userId=1
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| userId | Long | Yes | User ID |

**Success Response (200)**
```json
{
  "success": true,
  "message": "Reward history retrieved successfully",
  "data": [
    {
      "period": "2025-11",
      "points": 777,
      "distributedAt": "2025-11-21T10:32:15.000+0000",
      "rewardType": "MISSION_COMPLETION"
    }
  ]
}
```

---

### 6. Get Games List - GET `/api/games`

Get all available games.

**Request**
```
GET /api/games
```

**Success Response (200)**
```json
{
  "success": true,
  "message": "Games retrieved successfully",
  "data": [
    { "id": 1, "name": "Pharaoh Treasure", "description": "Egyptian themed slot game" },
    { "id": 2, "name": "Ocean Quest", "description": "Ocean adventure slot game" },
    { "id": 3, "name": "Nordic Gods", "description": "Norse mythology slot game" },
    { "id": 4, "name": "Lucky 777", "description": "Classic slot machine game" },
    { "id": 5, "name": "Magic Forest", "description": "Fantasy magic slot game" }
  ]
}
```

---

## Database Schema

### 1. users
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| username | VARCHAR(50) | Unique username |
| password | VARCHAR(255) | Password |
| points | INT | User points (default: 0) |
| created_at | TIMESTAMP | Creation time |
| updated_at | TIMESTAMP | Update time |

### 2. games
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| name | VARCHAR(100) | Unique game name |
| description | VARCHAR(500) | Game description |
| created_at | TIMESTAMP | Creation time |

### 3. missions
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| user_id | BIGINT | User ID |
| mission_type | VARCHAR(50) | Mission type |
| is_completed | BOOLEAN | Completion status |
| completed_at | TIMESTAMP | Completion time |
| created_at | TIMESTAMP | Creation time |
| updated_at | TIMESTAMP | Update time |

**Unique Key**: `(user_id, mission_type)`

### 4. games_play_record
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| user_id | BIGINT | User ID |
| game_id | BIGINT | Game ID |
| score | INT | Score for this session |
| played_at | TIMESTAMP | Play time |

### 5. user_login_records
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| user_id | BIGINT | User ID |
| login_date | DATE | Login date |
| created_at | TIMESTAMP | Creation time |

**Unique Key**: `(user_id, login_date)`

### 6. user_game_launches
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| user_id | BIGINT | User ID |
| game_id | BIGINT | Game ID |
| first_launched_at | TIMESTAMP | First launch time |

**Unique Key**: `(user_id, game_id)`

### 7. mission_rewards
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| user_id | BIGINT | User ID |
| reward_type | VARCHAR(50) | Reward type |
| reward_period | VARCHAR(20) | Reward period (yyyy-MM) |
| points | INT | Reward points |
| distributed_at | TIMESTAMP | Distribution time |

**Unique Key**: `(user_id, reward_type, reward_period)`

---

## Test Examples

### Complete Mission Flow Test

**Test User Credentials**
- Username: `test_user` (or `test_user1` ~ `test_user4`)
- Password: `password123`

### Mission 1: Consecutive Login 3 Days

```bash
# Day 1
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test_user","password":"password123","loginDate":"2025-11-19"}'

# Day 2
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test_user","password":"password123","loginDate":"2025-11-20"}'

# Day 3 (Mission Completed)
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test_user","password":"password123","loginDate":"2025-11-21"}'
```

### Mission 2: Launch 3 Different Games

```bash
# Launch Game 1
curl -X POST http://localhost:8080/api/launchGame \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"gameId":1}'

# Launch Game 2
curl -X POST http://localhost:8080/api/launchGame \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"gameId":2}'

# Launch Game 3 (Mission Completed)
curl -X POST http://localhost:8080/api/launchGame \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"gameId":3}'
```

### Mission 3: Play 3 Games with Total Score > 1000

```bash
# Play Game 1
curl -X POST http://localhost:8080/api/play \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"gameId":1}'

# Play Game 2
curl -X POST http://localhost:8080/api/play \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"gameId":2}'

# Play Game 3
curl -X POST http://localhost:8080/api/play \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"gameId":3}'

# Note: Score is random (0-1000). May need more plays to exceed 1000 total.
```

### Check Progress & Rewards

```bash
# Check mission progress
curl -X GET "http://localhost:8080/api/missions?userId=1"

# Check reward history
curl -X GET "http://localhost:8080/api/rewards?userId=1"

# Get games list
curl -X GET "http://localhost:8080/api/games"
```

### Reset Database (for testing)

```bash
docker exec -i mysql mysql -utaskuser -ptaskpass taskdb -e "
DELETE FROM missions;
DELETE FROM user_login_records;
DELETE FROM user_game_launches;
DELETE FROM games_play_record;
DELETE FROM mission_rewards;
UPDATE users SET points = 0;
"
```
