package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Login Request DTO
 */
@Data
public class LoginRequest {

    /**
     * Username (account)
     */
    @NotBlank(message = "Username is required")
    private String username;

    /**
     * Password
     */
    @NotBlank(message = "Password is required")
    private String password;

    /**
     * Login date (optional, for test simulation)
     * Format: "2025-11-19"
     * If not provided, today's date will be used
     */
    private String loginDate;
}
