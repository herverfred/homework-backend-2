package com.example.demo.consumer;

import com.example.demo.event.LoginEvent;
import com.example.demo.event.MissionCompletedEvent;
import com.example.demo.service.LoginService;
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

import java.sql.Date;
import java.time.Duration;
import java.util.UUID;

/**
 * Login Event Consumer
 * Handles LoginEvent asynchronously to:
 * 1. Initialize missions if needed
 * 2. Record login date
 * 3. Check if login mission (3 consecutive days) is completed
 * 4. Publish MissionCompletedEvent if completed
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
    private static final String MISSION_COMPLETED_TOPIC = "mission-completed-event";

    private final MissionInitService missionInitService;
    private final LoginService loginService;
    private final MissionService missionService;
    private final StringRedisTemplate redisTemplate;
    private RocketMQTemplate rocketMQTemplate;

    @Autowired(required = false)
    public void setRocketMQTemplate(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

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

            // 4. Publish MissionCompletedEvent if just completed
            if (justCompleted) {
                publishMissionCompletedEvent(userId, MissionInitService.MISSION_TYPE_LOGIN);
            }

            log.info("Successfully processed login event for user: {}, date: {}", userId, loginDate);

        } catch (Exception e) {
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
            .completedAt(new java.util.Date())
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
