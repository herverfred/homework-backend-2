package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Play Response DTO
 * Returns game session result, including user ID, game ID and generated score
 */
@Data
@Builder
public class PlayResponse {
    /**
     * User ID
     */
    private Long userId;

    /**
     * Game ID
     */
    private Long gameId;

    /**
     * Backend generated score (0-1000)
     */
    private Integer score;
}
