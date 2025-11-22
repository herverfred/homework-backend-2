#!/bin/bash
# Login API - User Login
# Records login event for consecutive login mission tracking

curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test_user",
    "password": "password123",
    "loginDate": "2025-11-22"
  }'
