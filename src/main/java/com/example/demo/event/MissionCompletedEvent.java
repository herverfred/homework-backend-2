package com.example.demo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Event representing mission completion
 * Published to RocketMQ when a user completes a mission
 * Consumed by MissionCompletedConsumer for reward distribution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionCompletedEvent {

    /**
     * Unique event ID for deduplication
     */
    private String eventId;

    /**
     * The user ID who completed the mission
     */
    private Long userId;

    /**
     * The type of mission that was completed
     * Values: CONSECUTIVE_LOGIN_3_DAYS, LAUNCH_3_DIFFERENT_GAMES, PLAY_3_GAMES_TOTAL_SCORE_1000
     */
    private String missionType;

    /**
     * Timestamp when the mission was completed
     */
    private Date completedAt;
}
