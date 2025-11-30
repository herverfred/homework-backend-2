package com.example.demo.service;

import com.example.demo.event.GameLaunchEvent;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.UserGameLaunchRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

/**
 * Game Launch Service
 * Simplified event-driven architecture: only validates user/game and publishes GameLaunchEvent
 * All business logic (mission initialization, recording, checking) handled by GameLaunchEventConsumer
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GameLaunchService {

    private final UserRepository userRepository;
    private final GameService gameService;
    private final UserGameLaunchRepository gameLaunchRepository;
    private final MissionService missionService;
    private RocketMQTemplate rocketMQTemplate;

    // RocketMQ topic for game launch events
    public static final String GAME_LAUNCH_EVENT_TOPIC = "mission-game-launch-event";

    @Autowired(required = false)
    public void setRocketMQTemplate(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * Handle game launch event
     * 1. Verify user and game exist
     * 2. Publish GameLaunchEvent to RocketMQ
     * 3. Return immediately (async processing by consumer)
     *
     * @param userId the user ID
     * @param gameId the game ID
     */
    public void handleLaunch(Long userId, Long gameId) {
        log.info("Handling game launch for user: {}, game: {}", userId, gameId);

        // 1. Verify user exists
        if (!userRepository.existsById(userId)) {
            log.error("User not found: {}", userId);
            throw new ResourceNotFoundException("User", userId);
        }

        // 2. Verify game exists
        if (!gameService.gameExists(gameId)) {
            log.error("Game not found: {}", gameId);
            throw new ResourceNotFoundException("Game", gameId);
        }

        // 3. Publish game launch event to RocketMQ
        publishGameLaunchEvent(userId, gameId);

        log.info("Game launch event published for user: {}, game: {}", userId, gameId);
    }

    /**
     * Publish game launch event to RocketMQ
     * Event will be consumed by GameLaunchEventConsumer for async processing
     *
     * @param userId the user ID
     * @param gameId the game ID
     */
    private void publishGameLaunchEvent(Long userId, Long gameId) {
        if (rocketMQTemplate == null) {
            log.warn("RocketMQTemplate not available, skipping event publishing");
            return;
        }

        GameLaunchEvent event = GameLaunchEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .userId(userId)
            .gameId(gameId)
            .launchTime(new Date())
            .build();

        rocketMQTemplate.asyncSend(GAME_LAUNCH_EVENT_TOPIC, event, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.debug("Game launch event sent successfully for user: {}, game: {}, msgId: {}",
                    event.getUserId(), event.getGameId(), sendResult.getMsgId());
            }

            @Override
            public void onException(Throwable e) {
                log.error("Failed to send game launch event, saving to outbox", e);
                missionService.saveFailedMessage(GAME_LAUNCH_EVENT_TOPIC, "GAME_LAUNCH",
                    event.getEventId(), event, e.getMessage());
            }
        });
        log.debug("Async game launch event published for user: {}, game: {} to topic: {}",
            userId, gameId, GAME_LAUNCH_EVENT_TOPIC);
    }

    /**
     * Record game launch for user
     * Uses INSERT IGNORE to handle concurrent inserts gracefully
     *
     * @param userId the user ID
     * @param gameId the game ID
     */
    @Transactional
    public void recordGameLaunch(Long userId, Long gameId) {
        gameLaunchRepository.insertIgnore(userId, gameId);
        log.debug("Game launch recorded for user: {}, game: {}", userId, gameId);
    }
}
