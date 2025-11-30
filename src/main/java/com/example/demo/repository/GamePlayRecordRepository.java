package com.example.demo.repository;

import com.example.demo.entity.GamePlayRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Repository
public interface GamePlayRecordRepository extends JpaRepository<GamePlayRecord, Long> {

    /**
     * Get gameplay statistics for a user (count and total score)
     *
     * @param userId the user ID
     * @return GamePlayStats projection with count and totalScore
     */
    @Query("SELECT COUNT(g) as count, COALESCE(SUM(g.score), 0) as totalScore FROM GamePlayRecord g WHERE g.userId = :userId")
    GamePlayStats getGamePlayStatsByUserId(@Param("userId") Long userId);

    /**
     * Get gameplay statistics within date range (for 30-day mission)
     *
     * @param userId the user ID
     * @param sinceDate the start date (30 days ago)
     * @return GamePlayStats with count and totalScore
     */
    @Query("SELECT COUNT(g) as count, COALESCE(SUM(g.score), 0) as totalScore FROM GamePlayRecord g WHERE g.userId = :userId AND g.playedAt >= :sinceDate")
    GamePlayStats getGamePlayStatsByUserIdAndPlayedAtAfter(@Param("userId") Long userId, @Param("sinceDate") Date sinceDate);

    /**
     * Insert game play record, ignore if duplicate (MySQL INSERT IGNORE)
     * Uses eventId for idempotency - duplicate eventIds are silently ignored
     *
     * @param eventId the event ID for idempotency
     * @param userId the user ID
     * @param gameId the game ID
     * @param score the score
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT IGNORE INTO games_play_record (event_id, user_id, game_id, score, played_at) " +
           "VALUES (:eventId, :userId, :gameId, :score, NOW())", nativeQuery = true)
    void insertIgnore(@Param("eventId") String eventId,
                      @Param("userId") Long userId,
                      @Param("gameId") Long gameId,
                      @Param("score") Integer score);

    /**
     * Projection interface for gameplay statistics
     */
    interface GamePlayStats {
        Long getCount();
        Long getTotalScore();
    }
}
