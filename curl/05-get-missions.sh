#!/bin/bash
# Get Missions API - Retrieve mission progress
# Returns progress for all three missions:
# 1. Log in for 3 consecutive days
# 2. Launch at least 3 different games
# 3. Play at least 3 games with total score > 1000

curl -X GET "http://localhost:8080/api/missions?userId=1"
