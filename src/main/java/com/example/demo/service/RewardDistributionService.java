package com.example.demo.service;

import com.example.demo.entity.MissionReward;
import com.example.demo.repository.MissionRewardRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Reward Distribution Service
 * Handles idempotent reward distribution when all missions are completed
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RewardDistributionService {

    private final UserRepository userRepository;
    private final MissionRewardRepository missionRewardRepository;
    private final MissionService missionService;

    // Reward constants
    public static final String REWARD_TYPE_MISSION_COMPLETION = "MISSION_COMPLETION";
    public static final Integer REWARD_POINTS = 777;

    private static final SimpleDateFormat PERIOD_FORMATTER = new SimpleDateFormat("yyyy-MM");

    /**
     * Distribute reward to user (idempotent operation)
     * Awards 777 points when all 3 missions are completed
     *
     * @param userId the user ID to award
     * @return true if reward was distributed, false if already distributed
     */
    @Transactional
    public boolean distributeReward(Long userId) {
        log.info("Attempting to distribute reward to user: {}", userId);

        // Check if all missions are completed
        if (!missionService.areAllMissionsCompleted(userId)) {
            log.debug("Not all missions completed for user: {}, skipping reward", userId);
            return false;
        }

        // Get current period (yyyy-MM format)
        String currentPeriod = getCurrentPeriod();

        //returns 1 if inserted, 0 if already exists
        int inserted = missionRewardRepository.insertIgnore(
            userId, REWARD_TYPE_MISSION_COMPLETION, currentPeriod, REWARD_POINTS);

        if (inserted == 0) {
            log.info("Reward already distributed to user: {} in period: {}", userId, currentPeriod);
            return false;
        }

        // Add points to user account
        int updatedRows = userRepository.addPoints(userId, REWARD_POINTS);

        if (updatedRows == 0) {
            log.error("Failed to add points to user: {} - user may not exist", userId);
            throw new RuntimeException("Failed to add points to user: " + userId);
        }

        log.info("Successfully distributed {} points to user: {}", REWARD_POINTS, userId);
        return true;
    }

    /**
     * Get reward history for a user
     *
     * @param userId the user ID
     * @return list of rewards ordered by most recent first
     */
    public List<MissionReward> getRewardHistory(Long userId) {
        log.debug("Fetching reward history for user: {}", userId);

        try {
            List<MissionReward> rewards = missionRewardRepository
                .findByUserIdOrderByDistributedAtDesc(userId);
            log.debug("Found {} rewards for user: {}", rewards.size(), userId);
            return rewards;

        } catch (Exception e) {
            log.error("Failed to fetch reward history for user: {}", userId, e);
            throw new RuntimeException("Failed to fetch reward history for user: " + userId, e);
        }
    }

    /**
     * Get current period in yyyy-MM format
     *
     * @return current period string (e.g., "2025-11")
     */
    public String getCurrentPeriod() {
        return PERIOD_FORMATTER.format(new java.util.Date());
    }

}
