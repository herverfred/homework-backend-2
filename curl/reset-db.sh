#!/bin/bash
# Reset Database - Clear all business records
# Preserves initial users and games data

echo "=========================================="
echo "Clearing Database Records"
echo "=========================================="

# Order matters due to foreign key constraints
docker exec mysql mysql -utaskuser -ptaskpass taskdb -e "
-- Clear reward records
DELETE FROM mission_rewards;

-- Clear mission records
DELETE FROM missions;

-- Clear game play records
DELETE FROM games_play_record;

-- Clear game launch records
DELETE FROM user_game_launches;

-- Clear login records
DELETE FROM user_login_records;

-- Reset user points
UPDATE users SET points = 0;

SELECT 'Database cleared successfully!' AS status;
"

echo ""
echo "=========================================="
echo "Database Cleared!"
echo "=========================================="

# Also clear Redis cache
echo ""
echo "Clearing Redis cache..."
docker exec redis redis-cli FLUSHALL

echo "Redis cache cleared!"
