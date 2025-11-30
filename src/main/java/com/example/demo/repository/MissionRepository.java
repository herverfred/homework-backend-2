package com.example.demo.repository;

import com.example.demo.entity.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface MissionRepository extends JpaRepository<Mission, Long> {

    /**
     * Find all missions for a specific user
     *
     * @param userId the user ID
     * @return list of missions for the user
     */
    List<Mission> findByUserId(Long userId);

    /**
     * Find missions for user within a valid cycle (cycle_start_date >= sinceDate)
     *
     * @param userId the user ID
     * @param sinceDate the minimum cycle start date
     * @return list of missions in valid cycle
     */
    List<Mission> findByUserIdAndCycleStartDateGreaterThanEqual(Long userId, Date sinceDate);

    /**
     * Count total missions for a user
     *
     * @param userId the user ID
     * @return total number of missions
     */
    long countByUserId(Long userId);

    /**
     * Count missions in valid cycle
     *
     * @param userId the user ID
     * @param sinceDate the minimum cycle start date
     * @return number of missions in valid cycle
     */
    long countByUserIdAndCycleStartDateGreaterThanEqual(Long userId, Date sinceDate);

    /**
     * Count missions by completion status for a user
     *
     * @param userId the user ID
     * @param isCompleted completion status
     * @return number of missions matching the criteria
     */
    long countByUserIdAndIsCompleted(Long userId, boolean isCompleted);

    /**
     * Count completed missions in valid cycle
     *
     * @param userId the user ID
     * @param sinceDate the minimum cycle start date
     * @param isCompleted completion status
     * @return number of completed missions in valid cycle
     */
    long countByUserIdAndCycleStartDateGreaterThanEqualAndIsCompleted(
        Long userId, Date sinceDate, Boolean isCompleted);

    /**
     * Insert a mission if it doesn't already exist (using INSERT IGNORE)
     * This handles concurrent inserts gracefully by ignoring duplicate key errors
     *
     * @param userId the user ID
     * @param missionType the mission type
     * @param cycleStartDate the cycle start date
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO missions (user_id, mission_type, cycle_start_date, is_completed, created_at) " +
                   "VALUES (:userId, :missionType, :cycleStartDate, false, NOW())",
           nativeQuery = true)
    void insertIgnore(@Param("userId") Long userId,
                      @Param("missionType") String missionType,
                      @Param("cycleStartDate") Date cycleStartDate);

    /**
     * @param id the mission ID
     * @param completedAt the completion timestamp
     * @return 1 if updated successfully, 0 if already completed by another thread
     */
    @Modifying
    @Query("UPDATE Mission m SET m.isCompleted = true, m.completedAt = :completedAt, m.updatedAt = :completedAt " +
           "WHERE m.id = :id AND m.isCompleted = false")
    int markAsCompleted(@Param("id") Long id, @Param("completedAt") Date completedAt);
}
