package com.example.demo.service;

import com.example.demo.entity.GamePlayRecord;
import com.example.demo.event.GamePlayEvent;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.GamePlayRecordRepository;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Game Play Service
 * Simplified event-driven architecture: only validates parameters and publishes GamePlayEvent
 * All business logic (mission initialization, recording, checking) handled by GamePlayEventConsumer
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GamePlayService {

    private final UserRepository userRepository;
    private final GameService gameService;
    private final GamePlayRecordRepository gamePlayRecordRepository;
    private RocketMQTemplate rocketMQTemplate;

    // RocketMQ topic for game play events
    public static final String GAME_PLAY_EVENT_TOPIC = "mission-game-play-event";

    @Autowired(required = false)
    public void setRocketMQTemplate(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * Handle game play event
     * 1. Generate random score (0-1000)
     * 2. Verify user and game exist
     * 3. Publish GamePlayEvent to RocketMQ
     * 4. Return generated score
     *
     * @param userId the user ID
     * @param gameId the game ID
     * @return generated score (0-1000)
     */
    public Integer handlePlay(Long userId, Long gameId) {
        // Generate random score between 0 and 1000 (inclusive)
        int score = ThreadLocalRandom.current().nextInt(350, 501);

        log.info("Handling game play for user: {}, game: {}, generated score: {}", userId, gameId, score);

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

        // 3. Publish game play event to RocketMQ
        publishGamePlayEvent(userId, gameId, score);

        log.info("Game play event published for user: {}, game: {}, generated score: {}", userId, gameId, score);

        // 4. Return generated score
        return score;
    }

    /**
     * Publish game play event to RocketMQ
     * Event will be consumed by GamePlayEventConsumer for async processing
     *
     * @param userId the user ID
     * @param gameId the game ID
     * @param score the score achieved
     */
    private void publishGamePlayEvent(Long userId, Long gameId, Integer score) {
        if (rocketMQTemplate == null) {
            log.warn("RocketMQTemplate not available, skipping event publishing");
            return;
        }

        GamePlayEvent event = GamePlayEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .userId(userId)
            .gameId(gameId)
            .score(score)
            .playTime(new Date())
            .build();

        rocketMQTemplate.asyncSend(GAME_PLAY_EVENT_TOPIC, event, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.debug("Game play event sent successfully for user: {}, game: {}, score: {}, msgId: {}",
                    event.getUserId(), event.getGameId(), event.getScore(), sendResult.getMsgId());
            }

            @Override
            public void onException(Throwable e) {
                log.error("Failed to send game play event for user: {}, game: {}, score: {}",
                    event.getUserId(), event.getGameId(), event.getScore(), e);
            }
        });
        log.debug("Async game play event published for user: {}, game: {}, score: {} to topic: {}",
            userId, gameId, score, GAME_PLAY_EVENT_TOPIC);
    }

    /**
     * Record gameplay session
     *
     * @param userId the user ID
     * @param gameId the game ID
     * @param score the score achieved
     */
    @Transactional
    public void recordGamePlay(Long userId, Long gameId, Integer score) {
        GamePlayRecord playRecord = GamePlayRecord.builder()
            .userId(userId)
            .gameId(gameId)
            .score(score)
            .build();

        gamePlayRecordRepository.save(playRecord);
        log.debug("Game play recorded for user: {}, game: {}, score: {}", userId, gameId, score);
    }
}
