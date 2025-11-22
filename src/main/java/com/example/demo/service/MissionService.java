package com.example.demo.service;

import com.example.demo.entity.Mission;
import com.example.demo.entity.UserLoginRecord;
import com.example.demo.repository.GamePlayRecordRepository;
import com.example.demo.repository.MissionRepository;
import com.example.demo.repository.UserGameLaunchRepository;
import com.example.demo.repository.UserLoginRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

/**
 * Mission Service with Redis Caching
 * Manages mission progress tracking and completion checking
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MissionService {

    private final MissionRepository missionRepository;
    private final UserLoginRecordRepository loginRecordRepository;
    private final UserGameLaunchRepository gameLaunchRepository;
    private final GamePlayRecordRepository gamePlayRecordRepository;
    private final StringRedisTemplate redisTemplate;
    private final CacheManager cacheManager;

    /**
     * Get mission progress for a user (with Redis caching)
     * Cache key: "missionProgress::userId"
     *
     * @param userId the user ID
     * @return list of missions with current progress
     */
    @Cacheable(value = "missionProgress", key = "#userId")
    public List<Mission> getMissionProgress(Long userId) {
        log.info("Fetching mission progress for user: {} (from DB)", userId);

        try {
            List<Mission> missions = missionRepository.findByUserId(userId);
            log.debug("Found {} missions for user: {}", missions.size(), userId);
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
            // Get last 10 login records ordered by date descending
            List<UserLoginRecord> records = loginRecordRepository
                .findTop10ByUserIdOrderByLoginDateDesc(userId);

            if (records.isEmpty()) {
                log.debug("No login records found for user: {}", userId);
                return 0;
            }

            // Start from today and count backwards
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date today = calendar.getTime();

            int consecutiveDays = 0;
            Calendar expectedCal = (Calendar) calendar.clone();

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
            long uniqueGamesLaunched = gameLaunchRepository.countByUserId(userId);
            boolean isCompleted = uniqueGamesLaunched >= 3;
            log.debug("Launch mission for user {}: {} games launched, completed: {}",
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
            GamePlayRecordRepository.GamePlayStats stats =
                gamePlayRecordRepository.getGamePlayStatsByUserId(userId);

            long playCount = stats.getCount();
            long totalScore = stats.getTotalScore();

            boolean isCompleted = playCount >= 3 && totalScore > 1000;
            log.debug("Play mission for user {}: {} plays, {} total score, completed: {}",
                userId, playCount, totalScore, isCompleted);
            return isCompleted;

        } catch (Exception e) {
            log.error("Failed to check play mission for user: {}", userId, e);
            return false;
        }
    }

    /**
     * Update mission completion status
     *
     * @param userId the user ID
     * @param missionType the mission type to update
     * @param isCompleted the completion status
     */
    @Transactional
    public void updateMissionCompletion(Long userId, String missionType, boolean isCompleted) {
        log.info("Updating mission {} for user {} to completed: {}", missionType, userId, isCompleted);

        try {
            List<Mission> missions = missionRepository.findByUserId(userId);

            for (Mission mission : missions) {
                if (mission.getMissionType().equals(missionType) && !mission.getIsCompleted()) {
                    mission.setIsCompleted(isCompleted);
                    if (isCompleted) {
                        mission.setCompletedAt(new Date());
                    }
                    missionRepository.save(mission);
                    log.info("Mission {} completed for user {}", missionType, userId);
                    break;
                }
            }

        } catch (Exception e) {
            log.error("Failed to update mission completion for user: {}, mission: {}",
                userId, missionType, e);
            throw new RuntimeException("Failed to update mission completion", e);
        }
    }

    /**
     * Check if all missions are completed for a user
     *
     * @param userId the user ID
     * @return true if all 3 missions are completed
     */
    public boolean areAllMissionsCompleted(Long userId) {
        try {
            long completedCount = missionRepository.countByUserIdAndIsCompleted(userId, true);
            boolean allCompleted = completedCount >= 3;
            log.debug("User {} has {} completed missions, all completed: {}",
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
            return gameLaunchRepository.countByUserId(userId);
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
            return gamePlayRecordRepository.getGamePlayStatsByUserId(userId);
        } catch (Exception e) {
            log.error("Failed to get gameplay stats for user: {}", userId, e);
            throw new RuntimeException("Failed to get gameplay stats for user: " + userId, e);
        }
    }

    /**
     * Check and complete mission if conditions are met
     *
     * @param userId the user ID
     * @param missionType the mission type
     * @param completionChecker supplier that checks if mission conditions are met
     * @return true if mission was just completed, false if already completed or not yet met
     */
    @Transactional
    public boolean checkAndComplete(Long userId, String missionType,
            Supplier<Boolean> completionChecker) {

        // Check if mission already completed
        List<Mission> missions = missionRepository.findByUserId(userId);
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
            mission.setIsCompleted(true);
            mission.setCompletedAt(new Date());
            missionRepository.save(mission);

            // Evict cache so getMissionProgress returns updated data
            evictMissionProgressCache(userId);

            log.info("Mission {} completed for user {}", missionType, userId);
            return true;
        }

        return false;
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
