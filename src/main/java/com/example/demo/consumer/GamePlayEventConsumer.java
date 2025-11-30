package com.example.demo.consumer;

import com.example.demo.event.GamePlayEvent;
import com.example.demo.service.GamePlayService;
import com.example.demo.service.MissionInitService;
import com.example.demo.service.MissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Game Play Event Consumer
 * Handles GamePlayEvent asynchronously to:
 * 1. Initialize missions if needed (protected by Redis distributed lock)
 * 2. Record game play session
 * 3. Check and complete play mission (event publishing is handled in MissionService)
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

    private final MissionInitService missionInitService;
    private final GamePlayService gamePlayService;
    private final MissionService missionService;
    private final StringRedisTemplate redisTemplate;

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
            String eventId = event.getEventId();

            // 1. Initialize missions if needed (protected by Redis distributed lock)
            missionInitService.initMissionsIfNotExist(userId);

            // 2. Record game play session (commits immediately with its own transaction)
            gamePlayService.recordGamePlay(eventId, userId, gameId, score);

            // 3. Check and complete mission
            boolean justCompleted = missionService.checkAndComplete(
                userId,
                MissionInitService.MISSION_TYPE_PLAY,
                () -> missionService.isPlayMissionCompleted(userId)
            );

            // 4. Send event after transaction commits (so consumer can see updated data)
            if (justCompleted) {
                missionService.publishMissionCompletedEvent(userId, MissionInitService.MISSION_TYPE_PLAY);
            }

            log.info("Successfully processed game play event for user: {}, game: {}, score: {}",
                userId, gameId, score);

        } catch (Exception e) {
            if (event.getEventId() != null) {
                String key = DEDUP_KEY_PREFIX + event.getEventId();
                redisTemplate.delete(key);
            }
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
}
