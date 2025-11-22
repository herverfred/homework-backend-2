#!/bin/bash
# Get Rewards API - Retrieve reward history
# Returns reward distribution records (777 points awarded upon completing all missions)

curl -X GET "http://localhost:8080/api/rewards?userId=1"
