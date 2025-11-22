package com.example.demo.repository;

import com.example.demo.entity.GamePlayRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * Projection interface for gameplay statistics
     */
    interface GamePlayStats {
        Long getCount();
        Long getTotalScore();
    }
}
