package com.example.demo.consumer;

import com.example.demo.event.LoginEvent;
import com.example.demo.service.LoginService;
import com.example.demo.service.MissionInitService;
import com.example.demo.service.MissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.Duration;

/**
 * Login Event Consumer
 * Handles LoginEvent asynchronously to:
 * 1. Initialize missions if needed
 * 2. Record login date
 * 3. Check and complete login mission (event publishing is handled in MissionService)
 */
@Component
@Slf4j
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = LoginService.LOGIN_EVENT_TOPIC,
    consumerGroup = "mission-login-consumer-group"
)
public class LoginEventConsumer implements RocketMQListener<LoginEvent> {

    private static final String DEDUP_KEY_PREFIX = "processed:login:";
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    private final MissionInitService missionInitService;
    private final LoginService loginService;
    private final MissionService missionService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(LoginEvent event) {
        log.info("Received login event for user: {}, date: {}, eventId: {}",
            event.getUserId(), event.getLoginDate(), event.getEventId());

        // Deduplication check
        if (event.getEventId() != null && isDuplicateMessage(event.getEventId())) {
            log.info("Duplicate login event, skipping: eventId={}", event.getEventId());
            return;
        }

        try {
            Long userId = event.getUserId();
            Date loginDate = event.getLoginDate();

            // 1. Initialize missions if needed
            missionInitService.initMissionsIfNotExist(userId);

            // 2. Record login date (commits immediately with its own transaction)
            loginService.recordLogin(userId, loginDate);

            // 3. Check and complete mission
            boolean justCompleted = missionService.checkAndComplete(
                userId,
                MissionInitService.MISSION_TYPE_LOGIN,
                () -> missionService.isLoginMissionCompleted(userId)
            );

            // 4. Send event after transaction commits (so consumer can see updated data)
            if (justCompleted) {
                missionService.publishMissionCompletedEvent(userId, MissionInitService.MISSION_TYPE_LOGIN);
            }

            log.info("Successfully processed login event for user: {}, date: {}", userId, loginDate);

        } catch (Exception e) {
            if (event.getEventId() != null) {
                String key = DEDUP_KEY_PREFIX + event.getEventId();
                redisTemplate.delete(key);
            }
            log.error("Failed to process login event for user: {}", event.getUserId(), e);
            throw new RuntimeException("Failed to process login event", e);
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
