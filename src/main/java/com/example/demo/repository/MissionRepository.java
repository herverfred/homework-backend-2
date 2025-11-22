package com.example.demo.repository;

import com.example.demo.entity.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * Count total missions for a user
     *
     * @param userId the user ID
     * @return total number of missions
     */
    long countByUserId(Long userId);

    /**
     * Count missions by completion status for a user
     *
     * @param userId the user ID
     * @param isCompleted completion status
     * @return number of missions matching the criteria
     */
    long countByUserIdAndIsCompleted(Long userId, boolean isCompleted);

    /**
     * Insert a mission if it doesn't already exist (using INSERT IGNORE)
     * This handles concurrent inserts gracefully by ignoring duplicate key errors
     *
     * @param userId the user ID
     * @param missionType the mission type
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO missions (user_id, mission_type, is_completed, created_at) " +
                   "VALUES (:userId, :missionType, false, NOW())",
           nativeQuery = true)
    void insertIgnore(@Param("userId") Long userId, @Param("missionType") String missionType);
}
