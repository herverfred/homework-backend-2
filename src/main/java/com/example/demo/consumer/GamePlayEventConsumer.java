package com.example.demo.consumer;

import com.example.demo.event.GamePlayEvent;
import com.example.demo.event.MissionCompletedEvent;
import com.example.demo.service.GamePlayService;
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
 * Game Play Event Consumer
 * Handles GamePlayEvent asynchronously to:
 * 1. Initialize missions if needed (protected by Redis distributed lock)
 * 2. Record game play session
 * 3. Check if play mission (3+ games with total score > 1000) is completed
 * 4. Publish MissionCompletedEvent if completed
 */
@Component
@Slf4j
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = GamePlayService.GAME_PLAY_EVENT_TOPIC,
    consumerGroup = "mission-game-play-consumer-group"
)
public class GamePlayEventConsumer implements RocketMQListener<GamePlayEvent> {

    private static final String DEDUP_KEY_PREFIX = "processed:game-play:";
    private static final Duration DEDUP_TTL = Duration.ofHours(24);
    private static final String MISSION_COMPLETED_TOPIC = "mission-completed-event";

    private final MissionInitService missionInitService;
    private final GamePlayService gamePlayService;
    private final MissionService missionService;
    private final StringRedisTemplate redisTemplate;
    private RocketMQTemplate rocketMQTemplate;

    @Autowired(required = false)
    public void setRocketMQTemplate(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Override
    public void onMessage(GamePlayEvent event) {
        log.info("Received game play event for user: {}, game: {}, score: {}, eventId: {}",
            event.getUserId(), event.getGameId(), event.getScore(), event.getEventId());

        // Deduplication check
        if (event.getEventId() != null && isDuplicateMessage(event.getEventId())) {
            log.info("Duplicate game play event, skipping: eventId={}", event.getEventId());
            return;
        }

        try {
            Long userId = event.getUserId();
            Long gameId = event.getGameId();
            Integer score = event.getScore();

            // 1. Initialize missions if needed (protected by Redis distributed lock)
            missionInitService.initMissionsIfNotExist(userId);

            // 2. Record game play session (commits immediately with its own transaction)
            gamePlayService.recordGamePlay(userId, gameId, score);

            // 3. Check and complete mission
            boolean justCompleted = missionService.checkAndComplete(
                userId,
                MissionInitService.MISSION_TYPE_PLAY,
                () -> missionService.isPlayMissionCompleted(userId)
            );

            // 4. Publish MissionCompletedEvent if just completed
            if (justCompleted) {
                publishMissionCompletedEvent(userId, MissionInitService.MISSION_TYPE_PLAY);
            }

            log.info("Successfully processed game play event for user: {}, game: {}, score: {}",
                userId, gameId, score);

        } catch (Exception e) {
            log.error("Failed to process game play event for user: {}, game: {}, score: {}",
                event.getUserId(), event.getGameId(), event.getScore(), e);
            throw new RuntimeException("Failed to process game play event", e);
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
