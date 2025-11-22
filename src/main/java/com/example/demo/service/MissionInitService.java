package com.example.demo.service;

import com.example.demo.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Mission Initialization Service
 * Responsible for initializing the 3 core missions for new users
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MissionInitService {

    private final MissionRepository missionRepository;
    private final StringRedisTemplate redisTemplate;

    // Mission type constants
    public static final String MISSION_TYPE_LOGIN = "CONSECUTIVE_LOGIN_3_DAYS";
    public static final String MISSION_TYPE_LAUNCH = "LAUNCH_3_DIFFERENT_GAMES";
    public static final String MISSION_TYPE_PLAY = "PLAY_3_GAMES_TOTAL_SCORE_1000";

    // Redis lock configuration
    private static final String LOCK_PREFIX = "mission:init:";
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Initialize missions for a user if they don't exist
     * Creates 3 missions:
     * 1. Log in for 3 consecutive days
     * 2. Launch at least 3 different games
     * 3. Play at least 3 game sessions with combined score > 1,000 points
     *
     * Uses Redis distributed lock to prevent concurrent initialization.
     * Uses INSERT IGNORE to handle edge cases gracefully.
     *
     * @param userId the user ID to initialize missions for
     */
    @Transactional
    public void initMissionsIfNotExist(Long userId) {
        String lockKey = LOCK_PREFIX + userId;

        // Try to acquire distributed lock
        Boolean locked = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", LOCK_TIMEOUT);

        if (!Boolean.TRUE.equals(locked)) {
            log.debug("Mission init lock not acquired for user {}, skipping (another thread is handling)", userId);
            return;
        }

        try {
            log.info("Initializing missions for user: {}", userId);

            List<String> missionTypes = Arrays.asList(
                MISSION_TYPE_LOGIN,
                MISSION_TYPE_LAUNCH,
                MISSION_TYPE_PLAY
            );

            for (String missionType : missionTypes) {
                missionRepository.insertIgnore(userId, missionType);
                log.debug("Ensured mission {} exists for user {}", missionType, userId);
            }

            log.info("Mission initialization completed for user: {}", userId);

        } finally {
            // Always release the lock
            redisTemplate.delete(lockKey);
        }
    }
}
