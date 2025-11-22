package com.example.demo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Date;

/**
 * Login Event
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginEvent {

    /**
     * Unique event ID for deduplication
     */
    private String eventId;

    /**
     * User ID
     */
    private Long userId;

    /**
     * Login date
     */
    private Date loginDate;
}
