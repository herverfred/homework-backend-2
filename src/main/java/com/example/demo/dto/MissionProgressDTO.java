package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionProgressDTO {
    private Long userId;
    private MissionDetail loginMission;
    private MissionDetail launchMission;
    private MissionDetail playMission;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissionDetail {
        private String type;
        private String description;
        private Integer currentProgress;
        private Integer targetProgress;
        private String progressText;
        private boolean completed;
        private Date completedAt;
        private Map<String, Object> extraInfo;
    }
}
