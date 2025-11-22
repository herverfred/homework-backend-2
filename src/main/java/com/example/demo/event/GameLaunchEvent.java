package com.example.demo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Game Launch Event
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameLaunchEvent {

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
     * Launch time
     */
    private Date launchTime;
}
