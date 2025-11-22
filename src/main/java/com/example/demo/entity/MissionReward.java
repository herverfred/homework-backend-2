package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Entity
@Table(name = "mission_rewards")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reward_type", nullable = false, length = 50)
    private String rewardType;

    @Column(name = "reward_period", nullable = false, length = 20)
    private String rewardPeriod;

    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "distributed_at", nullable = false, updatable = false)
    private Date distributedAt;

    @PrePersist
    protected void onCreate() {
        distributedAt = new Date();
    }
}
