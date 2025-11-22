package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Play Request DTO
 * Game session request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayRequest {

    /**
     * User ID
     */
    @NotNull(message = "User ID is required")
    private Long userId;

    /**
     * Game ID
     */
    @NotNull(message = "Game ID is required")
    private Long gameId;
}
