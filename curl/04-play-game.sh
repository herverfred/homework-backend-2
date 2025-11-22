#!/bin/bash
# Play Game API - Record gameplay session
# Server generates random score (0-1000)
# Tracks play count and total score for the "play 3 games with score > 1000" mission

curl -X POST http://localhost:8080/api/play \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "gameId": 1
  }'
