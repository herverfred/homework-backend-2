package com.example.demo.service;

import com.example.demo.entity.MessageOutbox;
import com.example.demo.entity.Mission;
import com.example.demo.entity.UserLoginRecord;
import com.example.demo.event.MissionCompletedEvent;
import com.example.demo.repository.GamePlayRecordRepository;
import com.example.demo.repository.MessageOutboxRepository;
import com.example.demo.repository.MissionRepository;
import com.example.demo.repository.UserGameLaunchRepository;
import com.example.demo.repository.UserLoginRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Mission Service with Redis Caching
 * Manages mission progress tracking and completion checking
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MissionService {

    private static final String MISSION_COMPLETED_TOPIC = "mission-completed-event";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_FAILED = "FAILED";

    private final MissionRepository missionRepository;
    private final UserLoginRecordRepository loginRecordRepository;
    private final UserGameLaunchRepository gameLaunchRepository;
    private final GamePlayRecordRepository gamePlayRecordRepository;
    private final MessageOutboxRepository outboxRepository;
    private final StringRedisTemplate redisTemplate;
    private final CacheManager cacheManager;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Get mission progress for a user (with Redis caching)
     * Only returns missions in valid 30-day cycle
     * Cache key: "missionProgress::userId"
     *
     * @param userId the user ID
     * @return list of missions with current progress in valid cycle
     */
    @Cacheable(value = "missionProgress", key = "#userId")
    public List<Mission> getMissionProgress(Long userId) {
        log.info("Fetching mission progress for user: {} (from DB)", userId);

        try {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -30);
            Date validSince = cal.getTime();
            List<Mission> missions = missionRepository
                .findByUserIdAndCycleStartDateGreaterThanEqual(userId, validSince);
            log.debug("Found {} missions in valid cycle for user: {}", missions.size(), userId);
            return missions;

        } catch (Exception e) {
            log.error("Failed to fetch mission progress for user: {}", userId, e);
            throw new RuntimeException("Failed to fetch mission progress for user: " + userId, e);
        }
    }

    /**
     * Calculate consecutive login days for a user
     * Checks the most recent login records to determine if user has logged in
     * for 3 consecutive days
     *
     * @param userId the user ID
     * @return number of consecutive login days (from today backwards)
     */
    public int calculateConsecutiveLoginDays(Long userId) {
        log.debug("Calculating consecutive login days for user: {}", userId);

        try {
            // Get login records within 30 days, ordered by date descending
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -30);
            Date thirtyDaysAgo = cal.getTime();

            List<UserLoginRecord> records = loginRecordRepository
                .findByUserIdAndLoginDateAfter(userId, thirtyDaysAgo);

            if (records.isEmpty()) {
                log.debug("No login records found for user: {}", userId);
                return 0;
            }

            // Start from the most recent login date and count backwards
            int consecutiveDays = 0;
            Calendar expectedCal = Calendar.getInstance();

            // Initialize expected date from first record (most recent, since ordered DESC)
            Date firstLoginDate = normalizeDateToMidnight(records.get(0).getLoginDate());
            expectedCal.setTime(firstLoginDate);

            for (UserLoginRecord record : records) {
                Date expectedDate = expectedCal.getTime();
                Date loginDate = normalizeDateToMidnight(record.getLoginDate());

                if (loginDate.equals(expectedDate)) {
                    consecutiveDays++;
                    expectedCal.add(Calendar.DAY_OF_MONTH, -1);
                } else if (loginDate.before(expectedDate)) {
                    // Gap in login dates, stop counting
                    break;
                }
            }

            log.debug("User {} has {} consecutive login days", userId, consecutiveDays);
            return consecutiveDays;

        } catch (Exception e) {
            log.error("Failed to calculate consecutive login days for user: {}", userId, e);
            return 0;
        }
    }

    /**
     * Check if login mission (3 consecutive days) is completed
     *
     * @param userId the user ID
     * @return true if mission is completed
     */
    public boolean isLoginMissionCompleted(Long userId) {
        int consecutiveDays = calculateConsecutiveLoginDays(userId);
        boolean isCompleted = consecutiveDays >= 3;
        log.debug("Login mission for user {}: {} consecutive days, completed: {}",
            userId, consecutiveDays, isCompleted);
        return isCompleted;
    }

    /**
     * Check if launch mission (3 different games) is completed
     *
     * @param userId the user ID
     * @return true if mission is completed
     */
    public boolean isLaunchMissionCompleted(Long userId) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -30);
            Date thirtyDaysAgo = cal.getTime();
            long uniqueGamesLaunched = gameLaunchRepository
                .countDistinctGamesByUserIdAndLaunchDateAfter(userId, thirtyDaysAgo);
            boolean isCompleted = uniqueGamesLaunched >= 3;
            log.debug("Launch mission for user {}: {} games launched in 30 days, completed: {}",
                userId, uniqueGamesLaunched, isCompleted);
            return isCompleted;

        } catch (Exception e) {
            log.error("Failed to check launch mission for user: {}", userId, e);
            return false;
        }
    }

    /**
     * Check if play mission (3+ games with total score > 1000) is completed
     *
     * @param userId the user ID
     * @return true if mission is completed
     */
    public boolean isPlayMissionCompleted(Long userId) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -30);
            Date thirtyDaysAgo = cal.getTime();

            GamePlayRecordRepository.GamePlayStats stats =
                gamePlayRecordRepository.getGamePlayStatsByUserIdAndPlayedAtAfter(userId, thirtyDaysAgo);

            long playCount = stats.getCount();
            long totalScore = stats.getTotalScore();

            boolean isCompleted = playCount >= 3 && totalScore > 1000;
            log.debug("Play mission for user {}: {} plays in 30 days, {} total score, completed: {}",
                userId, playCount, totalScore, isCompleted);
            return isCompleted;

        } catch (Exception e) {
            log.error("Failed to check play mission for user: {}", userId, e);
            return false;
        }
    }

    /**
     * Check if all missions are completed for a user (within valid 30-day cycle)
     *
     * @param userId the user ID
     * @return true if all 3 missions are completed in valid cycle
     */
    public boolean areAllMissionsCompleted(Long userId) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -30);
            Date validSince = cal.getTime();
            long completedCount = missionRepository
                .countByUserIdAndCycleStartDateGreaterThanEqualAndIsCompleted(userId, validSince, true);
            boolean allCompleted = completedCount >= 3;
            log.debug("User {} has {} completed missions in valid cycle, all completed: {}",
                userId, completedCount, allCompleted);
            return allCompleted;

        } catch (Exception e) {
            log.error("Failed to check if all missions completed for user: {}", userId, e);
            return false;
        }
    }

    /**
     * Get count of unique games launched by user
     * Helper method for controller to display mission progress
     *
     * @param userId the user ID
     * @return number of unique games launched
     */
    public long getUniqueGamesLaunchedCount(Long userId) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -30);
            Date thirtyDaysAgo = cal.getTime();
            return gameLaunchRepository
                .countDistinctGamesByUserIdAndLaunchDateAfter(userId, thirtyDaysAgo);
        } catch (Exception e) {
            log.error("Failed to get unique games launched count for user: {}", userId, e);
            return 0;
        }
    }

    /**
     * Get gameplay statistics for a user
     * Helper method for controller to display mission progress
     *
     * @param userId the user ID
     * @return GamePlayStats with count and total score
     */
    public GamePlayRecordRepository.GamePlayStats getGamePlayStats(Long userId) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -30);
            Date thirtyDaysAgo = cal.getTime();

            return gamePlayRecordRepository.getGamePlayStatsByUserIdAndPlayedAtAfter(userId, thirtyDaysAgo);
        } catch (Exception e) {
            log.error("Failed to get gameplay stats for user: {}", userId, e);
            throw new RuntimeException("Failed to get gameplay stats for user: " + userId, e);
        }
    }

    /**
     * Check and complete mission if conditions are met (within valid 30-day cycle)
     *
     * @param userId            the user ID
     * @param missionType       the mission type
     * @param completionChecker supplier that checks if mission conditions are met
     * @return true if mission was just completed, false otherwise
     */
    @Transactional
    public boolean checkAndComplete(Long userId, String missionType,
                                    Supplier<Boolean> completionChecker) {

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -30);
        Date validSince = cal.getTime();

        // Check if mission already completed (in valid cycle)
        List<Mission> missions = missionRepository
            .findByUserIdAndCycleStartDateGreaterThanEqual(userId, validSince);
        Mission mission = missions.stream()
            .filter(m -> m.getMissionType().equals(missionType))
            .findFirst()
            .orElse(null);

        if (mission == null) {
            log.warn("Mission {} not found for user {}", missionType, userId);
            return false;
        }

        if (mission.getIsCompleted()) {
            log.debug("Mission {} already completed for user {}", missionType, userId);
            return false;
        }

        // Check if conditions are met
        if (completionChecker.get()) {
            // Use CAS update - only one thread will succeed
            int updated = missionRepository.markAsCompleted(mission.getId(), new Date());

            if (updated > 0) {
                // This thread successfully completed the mission
                evictMissionProgressCache(userId);
                log.info("Mission {} completed for user {}", missionType, userId);
                return true;
            } else {
                // Another thread already completed this mission
                log.debug("Mission {} was already completed by another thread for user {}",
                    missionType, userId);
                return false;
            }
        }

        return false;
    }

    /**
     * Publish mission completed event (sync)
     * If sending fails, save to outbox for retry
     *
     * @param userId the user ID
     * @param missionType the mission type
     */
    public void publishMissionCompletedEvent(Long userId, String missionType) {
        MissionCompletedEvent event = MissionCompletedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .userId(userId)
            .missionType(missionType)
            .completedAt(new Date())
            .build();

        try {
            rocketMQTemplate.syncSend(MISSION_COMPLETED_TOPIC, event);
            log.info("Published mission completed event: userId={}, missionType={}", userId, missionType);
        } catch (Exception e) {
            log.error("Failed to send mission completed event, saving to outbox", e);
            saveFailedMessage(MISSION_COMPLETED_TOPIC, "MISSION_COMPLETED",
                event.getEventId(), event, e.getMessage());
        }
    }

    /**
     * Save failed message to outbox for retry
     * This method is public so other services can use it
     *
     * @param topic the RocketMQ topic
     * @param eventType the event type
     * @param eventId the unique event ID
     * @param payload the message payload
     * @param error the error message
     */
    public void saveFailedMessage(String topic, String eventType,
                                   String eventId, Object payload, String error) {
        try {
            MessageOutbox outbox = MessageOutbox.builder()
                .eventId(eventId)
                .topic(topic)
                .payload(objectMapper.writeValueAsString(payload))
                .eventType(eventType)
                .status(STATUS_PENDING)
                .retryCount(0)
                .maxRetries(10)
                .nextRetryAt(new Date(System.currentTimeMillis() + 30000))
                .errorMessage(error)
                .build();
            outboxRepository.save(outbox);
            log.info("Saved failed message to outbox: eventId={}, topic={}", eventId, topic);
        } catch (Exception e) {
            log.error("Failed to save message to outbox: {}", eventId, e);
        }
    }

    /**
     * Process outbox messages - retry sending failed messages
     * Runs every 30 seconds
     */
    @Scheduled(fixedDelay = 30000)
    public void processOutbox() {
        List<MessageOutbox> messages = outboxRepository
            .findByStatusAndNextRetryAtLessThanEqual(STATUS_PENDING, new Date());

        if (messages.isEmpty()) {
            return;
        }

        log.info("Processing {} pending outbox messages", messages.size());

        for (MessageOutbox msg : messages) {
            try {
                rocketMQTemplate.syncSend(msg.getTopic(), msg.getPayload());
                outboxRepository.delete(msg);
                log.info("Successfully resent message: eventId={}", msg.getEventId());
            } catch (Exception e) {
                msg.setRetryCount(msg.getRetryCount() + 1);
                msg.setErrorMessage(e.getMessage());

                if (msg.getRetryCount() >= msg.getMaxRetries()) {
                    msg.setStatus(STATUS_FAILED);
                    log.error("Message exceeded max retries: eventId={}", msg.getEventId());
                } else {
                    msg.setNextRetryAt(new Date(System.currentTimeMillis() + 30000));
                }
                outboxRepository.save(msg);
            }
        }
    }

    /**
     * Helper method to normalize a Date to midnight (00:00:00.000)
     * Used for date-only comparisons
     *
     * @param date the date to normalize
     * @return normalized date at midnight
     */
    private Date normalizeDateToMidnight(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * Evict mission progress cache for a user
     *
     * @param userId the user ID
     */
    private void evictMissionProgressCache(Long userId) {
        Cache cache = cacheManager.getCache("missionProgress");
        if (cache != null) {
            cache.evict(userId);
            log.debug("Evicted missionProgress cache for user: {}", userId);
        }
    }
}
