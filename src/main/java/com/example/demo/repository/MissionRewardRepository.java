package com.example.demo.repository;

import com.example.demo.entity.MissionReward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MissionRewardRepository extends JpaRepository<MissionReward, Long> {

    /**
     * Find all rewards for a user, ordered by distribution date descending
     *
     * @param userId the user ID
     * @return list of rewards ordered by most recent first
     */
    List<MissionReward> findByUserIdOrderByDistributedAtDesc(Long userId);

    /**
     * Insert reward record
     *
     * @param userId the user ID
     * @param rewardType the reward type
     * @param rewardPeriod the reward period (yyyy-MM)
     * @param points the points to award
     * @return 1 if inserted, 0 if already exists
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO mission_rewards (user_id, reward_type, reward_period, points, distributed_at) " +
           "VALUES (:userId, :rewardType, :rewardPeriod, :points, NOW())", nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId,
                     @Param("rewardType") String rewardType,
                     @Param("rewardPeriod") String rewardPeriod,
                     @Param("points") Integer points);
}
