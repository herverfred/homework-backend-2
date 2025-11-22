package com.example.demo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Game Play Event
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GamePlayEvent {

    /**
     * Unique event ID for deduplication
     */
    private String eventId;

    /**
     * User ID
     */
    private Long userId;

    /**
     * Game ID
     */
    private Long gameId;

    /**
     * Game session score
     */
    private Integer score;

    /**
     * Play time
     */
    private Date playTime;
}
