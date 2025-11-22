package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewardHistoryDTO {
    private String period;
    private Integer points;
    private Date distributedAt;
    private String rewardType;
}
