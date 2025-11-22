package com.example.demo.consumer;

import com.example.demo.event.GameLaunchEvent;
import com.example.demo.event.MissionCompletedEvent;
import com.example.demo.service.GameLaunchService;
import com.example.demo.service.MissionInitService;
import com.example.demo.service.MissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * Game Launch Event Consumer
 * Handles GameLaunchEvent asynchronously to:
 * 1. Initialize missions if needed (protected by Redis distributed lock)
 * 2. Record game launch
 * 3. Check if launch mission (3 different games) is completed
 * 4. Publish MissionCompletedEvent if completed
 */
@Component
@Slf4j
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = GameLaunchService.GAME_LAUNCH_EVENT_TOPIC,
    consumerGroup = "mission-game-launch-consumer-group"
)
public class GameLaunchEventConsumer implements RocketMQListener<GameLaunchEvent> {

    private static final String DEDUP_KEY_PREFIX = "processed:game-launch:";
    private static final Duration DEDUP_TTL = Duration.ofHours(24);
    private static final String MISSION_COMPLETED_TOPIC = "mission-completed-event";

    private final MissionInitService missionInitService;
    private final GameLaunchService gameLaunchService;
    private final MissionService missionService;
    private final StringRedisTemplate redisTemplate;
    private RocketMQTemplate rocketMQTemplate;

    @Autowired(required = false)
    public void setRocketMQTemplate(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Override
    public void onMessage(GameLaunchEvent event) {
        log.info("Received game launch event for user: {}, game: {}, eventId: {}",
            event.getUserId(), event.getGameId(), event.getEventId());

        // Deduplication check
        if (event.getEventId() != null && isDuplicateMessage(event.getEventId())) {
            log.info("Duplicate game launch event, skipping: eventId={}", event.getEventId());
            return;
        }

        try {
            Long userId = event.getUserId();
            Long gameId = event.getGameId();

            // 1. Initialize missions if needed (protected by Redis distributed lock)
            missionInitService.initMissionsIfNotExist(userId);

            // 2. Record game launch (commits immediately with its own transaction)
            gameLaunchService.recordGameLaunch(userId, gameId);

            // 3. Check and complete mission
            boolean justCompleted = missionService.checkAndComplete(
                userId,
                MissionInitService.MISSION_TYPE_LAUNCH,
                () -> missionService.isLaunchMissionCompleted(userId)
            );

            // 4. Publish MissionCompletedEvent if just completed
            if (justCompleted) {
                publishMissionCompletedEvent(userId, MissionInitService.MISSION_TYPE_LAUNCH);
            }

            log.info("Successfully processed game launch event for user: {}, game: {}", userId, gameId);

        } catch (Exception e) {
            log.error("Failed to process game launch event for user: {}, game: {}",
                event.getUserId(), event.getGameId(), e);
            throw new RuntimeException("Failed to process game launch event", e);
        }
    }

    /**
     * Check if this message has already been processed using Redis SETNX
     * Returns true if duplicate (already processed), false if new message
     *
     * @param eventId the event ID
     * @return true if duplicate, false if new
     */
    private boolean isDuplicateMessage(String eventId) {
        String key = DEDUP_KEY_PREFIX + eventId;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "1", DEDUP_TTL);
        return !Boolean.TRUE.equals(isNew);
    }

    /**
     * Publish MissionCompletedEvent to trigger reward distribution
     *
     * @param userId the user ID
     * @param missionType the mission type
     */
    private void publishMissionCompletedEvent(Long userId, String missionType) {
        if (rocketMQTemplate == null) {
            log.warn("RocketMQTemplate not available, skipping mission completed event publishing");
            return;
        }

        MissionCompletedEvent event = MissionCompletedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .userId(userId)
            .missionType(missionType)
            .completedAt(new Date())
            .build();

        rocketMQTemplate.asyncSend(MISSION_COMPLETED_TOPIC, event, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.debug("Mission completed event sent successfully for user: {}, mission: {}, msgId: {}",
                    event.getUserId(), event.getMissionType(), sendResult.getMsgId());
            }

            @Override
            public void onException(Throwable e) {
                log.error("Failed to send mission completed event for user: {}, mission: {}",
                    event.getUserId(), event.getMissionType(), e);
            }
        });
        log.info("Published mission completed event for user: {}, mission: {}", userId, missionType);
    }
}
