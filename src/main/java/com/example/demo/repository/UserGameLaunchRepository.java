package com.example.demo.repository;

import com.example.demo.entity.UserGameLaunch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;

@Repository
public interface UserGameLaunchRepository extends JpaRepository<UserGameLaunch, Long> {

    /**
     * Count the number of unique games launched by a user
     *
     * @param userId the user ID
     * @return number of unique games launched
     */
    long countByUserId(Long userId);

    /**
     * Check if a user has already launched a specific game
     *
     * @param userId the user ID
     * @param gameId the game ID
     * @return true if exists
     */
    boolean existsByUserIdAndGameId(Long userId, Long gameId);

    /**
     * Insert game launch record, ignore if duplicate (same user + game + date)
     *
     * @param userId the user ID
     * @param gameId the game ID
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO user_game_launches (user_id, game_id, launch_date, created_at) VALUES (:userId, :gameId, CURDATE(), NOW())", nativeQuery = true)
    void insertIgnore(@Param("userId") Long userId, @Param("gameId") Long gameId);

    /**
     * Count distinct games launched within date range (for 30-day mission)
     *
     * @param userId the user ID
     * @param sinceDate the start date
     * @return number of distinct games launched
     */
    @Query("SELECT COUNT(DISTINCT u.gameId) FROM UserGameLaunch u WHERE u.userId = :userId AND u.launchDate >= :sinceDate")
    long countDistinctGamesByUserIdAndLaunchDateAfter(@Param("userId") Long userId, @Param("sinceDate") Date sinceDate);
}
