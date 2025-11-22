package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Launch Game Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LaunchGameRequest {

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
