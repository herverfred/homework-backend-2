package com.example.demo.service;

import com.example.demo.entity.Mission;
import com.example.demo.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
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
    private final TransactionTemplate transactionTemplate;

    // Mission type constants
    public static final String MISSION_TYPE_LOGIN = "CONSECUTIVE_LOGIN_3_DAYS";
    public static final String MISSION_TYPE_LAUNCH = "LAUNCH_3_DIFFERENT_GAMES";
    public static final String MISSION_TYPE_PLAY = "PLAY_3_GAMES_TOTAL_SCORE_1000";

    // Redis lock configuration
    private static final String LOCK_PREFIX = "mission:init:";
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Initialize missions for a user if they don't exist in valid 30-day cycle.
     * Uses TransactionTemplate to ensure transaction commits before lock release.
     *
     * @param userId the user ID to initialize missions for
     */
    public void initMissionsIfNotExist(Long userId) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -30);
        Date validSince = cal.getTime();

        // First check: query DB for missions in valid cycle
        List<Mission> validMissions = missionRepository
            .findByUserIdAndCycleStartDateGreaterThanEqual(userId, validSince);

        if (validMissions.size() >= 3) {
            // Check if all 3 are completed (user already got reward, no need for new cycle)
            long completedCount = validMissions.stream()
                .filter(Mission::getIsCompleted)
                .count();

            if (completedCount >= 3) {
                log.debug("User {} already completed all missions in current cycle, skipping", userId);
                return;
            }

            // Has active cycle with incomplete missions
            log.debug("Missions already exist in valid cycle for user: {}", userId);
            return;
        }

        // Try to acquire distributed lock
        String lockKey = LOCK_PREFIX + userId;
        Boolean locked = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", LOCK_TIMEOUT);

        if (Boolean.TRUE.equals(locked)) {
            try {
                // Execute in transaction - commits before finally block
                transactionTemplate.executeWithoutResult(status -> {
                    // Double-check after acquiring lock
                    List<Mission> recheck = missionRepository
                        .findByUserIdAndCycleStartDateGreaterThanEqual(userId, validSince);
                    if (recheck.size() >= 3) {
                        log.debug("Missions already initialized by another thread for user: {}", userId);
                        return;
                    }

                    // Create new missions with normalized date (no time component)
                    Date cycleStartDate = java.sql.Date.valueOf(LocalDate.now());
                    log.info("Initializing missions for user: {} with cycle: {}", userId, cycleStartDate);

                    List<String> missionTypes = Arrays.asList(
                        MISSION_TYPE_LOGIN,
                        MISSION_TYPE_LAUNCH,
                        MISSION_TYPE_PLAY
                    );

                    for (String missionType : missionTypes) {
                        missionRepository.insertIgnore(userId, missionType, cycleStartDate);
                    }
                    log.info("Mission initialization completed for user: {}", userId);
                });
                // Transaction is committed here, data is visible to other threads
            } finally {
                // Safe to release lock now - transaction already committed
                redisTemplate.delete(lockKey);
            }
        } else {
            // Lock not acquired, wait for another thread to complete initialization
            waitForInitialization(userId, validSince);
        }
    }

    /**
     * Wait for another thread to complete mission initialization
     * Polls the database until missions are initialized or timeout
     *
     * @param userId the user ID
     * @param validSince the minimum cycle start date for valid missions
     * @throws RuntimeException if timeout waiting for initialization
     */
    private void waitForInitialization(Long userId, Date validSince) {
        log.debug("Waiting for mission initialization by another thread for user: {}", userId);

        for (int i = 0; i < 50; i++) {  // Wait up to 5 seconds (50 * 100ms)
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for mission init", e);
            }

            // Check DB to confirm initialization is complete (in valid cycle)
            if (missionRepository.countByUserIdAndCycleStartDateGreaterThanEqual(userId, validSince) >= 3) {
                log.debug("Mission init completed by another thread for user: {}", userId);
                return;
            }
        }

        // Timeout, throw exception to trigger message retry
        throw new RuntimeException("Timeout waiting for mission initialization for user: " + userId);
    }
}
