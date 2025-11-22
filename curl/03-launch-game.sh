#!/bin/bash
# Launch Game API - Record game launch event
# Tracks unique games launched for the "launch 3 different games" mission

curl -X POST http://localhost:8080/api/launchGame \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "gameId": 1
  }'
