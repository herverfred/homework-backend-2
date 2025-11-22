#!/bin/bash
# Complete Mission Test Script
# Tests all APIs and completes all three missions

BASE_URL="http://localhost:8080"

echo "=========================================="
echo "Mission Center API - Complete Test"
echo "=========================================="

# Check if server is running
echo -e "\nChecking server connectivity..."
if ! curl -s --connect-timeout 3 "$BASE_URL/api/games" > /dev/null 2>&1; then
  echo "ERROR: Cannot connect to server at $BASE_URL"
  echo "Please make sure the Spring Boot application is running."
  exit 1
fi
echo "Server is running!"

# 1. Consecutive Login 3 Days
echo -e "\n[1/6] Consecutive Login 3 Days Mission"
echo "----------------------------------------"
for day in "2025-11-20" "2025-11-21" "2025-11-22"; do
  echo "Login date: $day"
  curl -s -X POST "$BASE_URL/api/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\": \"test_user\", \"password\": \"password123\", \"loginDate\": \"$day\"}" | jq .
  sleep 2
done

# 2. Launch 3 Different Games
echo -e "\n[2/6] Launch 3 Different Games Mission"
echo "----------------------------------------"
for gameId in 1 2 3; do
  echo "Launching game: $gameId"
  curl -s -X POST "$BASE_URL/api/launchGame" \
    -H "Content-Type: application/json" \
    -d "{\"userId\": 1, \"gameId\": $gameId}" | jq .
  sleep 2
done

# 3. Play 3 Games (random scores)
echo -e "\n[3/6] Play 3 Games Mission"
echo "----------------------------------------"
for i in 1 2 3; do
  echo "Game play #$i"
  curl -s -X POST "$BASE_URL/api/play" \
    -H "Content-Type: application/json" \
    -d "{\"userId\": 1, \"gameId\": 1}" | jq .
  sleep 2
done

# Wait for async processing
echo -e "\nWaiting for async processing..."
sleep 5

# 4. Check Mission Progress
echo -e "\n[4/6] Mission Progress"
echo "----------------------------------------"
curl -s -X GET "$BASE_URL/api/missions?userId=1" | jq .

# 5. Check Rewards
echo -e "\n[5/6] Reward History"
echo "----------------------------------------"
curl -s -X GET "$BASE_URL/api/rewards?userId=1" | jq .

# 6. Get Games List
echo -e "\n[6/6] Games List"
echo "----------------------------------------"
curl -s -X GET "$BASE_URL/api/games" | jq .

echo -e "\n=========================================="
echo "Test Complete!"
echo "=========================================="
