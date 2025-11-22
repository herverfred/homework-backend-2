package com.example.demo.consumer;

import com.example.demo.event.MissionCompletedEvent;
import com.example.demo.service.MissionService;
import com.example.demo.service.RewardDistributionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * RocketMQ Consumer for Mission Completed Events
 * Listens to mission completion events and triggers reward distribution
 * when all missions are completed
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = "mission-completed-event",
    consumerGroup = "reward-distribution-group"
)
public class MissionCompletedConsumer implements RocketMQListener<MissionCompletedEvent> {

    private static final String DEDUP_KEY_PREFIX = "processed:mission-completed:";
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    private final RewardDistributionService rewardDistributionService;
    private final MissionService missionService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Process mission completed event
     * 1. Log the event
     * 2. Check if all missions are completed
     * 3. Distribute reward if all missions completed
     *
     * @param event the mission completed event
     */
    @Override
    public void onMessage(MissionCompletedEvent event) {
        log.info("Received mission completed event: userId={}, missionType={}, completedAt={}, eventId={}",
            event.getUserId(), event.getMissionType(), event.getCompletedAt(), event.getEventId());

        // Deduplication check
        if (event.getEventId() != null && isDuplicateMessage(event.getEventId())) {
            log.info("Duplicate mission completed event, skipping: eventId={}", event.getEventId());
            return;
        }

        try {
            Long userId = event.getUserId();

            // Check if all missions are completed
            boolean allCompleted = missionService.areAllMissionsCompleted(userId);

            if (allCompleted) {
                log.info("All missions completed for user: {}, attempting reward distribution", userId);

                // Distribute reward (idempotent operation)
                boolean rewardDistributed = rewardDistributionService.distributeReward(userId);

                if (rewardDistributed) {
                    log.info("Successfully distributed reward to user: {} after mission completion", userId);
                } else {
                    log.info("Reward already distributed for user: {} in current period", userId);
                }
            } else {
                log.debug("Not all missions completed yet for user: {}", userId);
            }

        } catch (Exception e) {
            log.error("Error processing mission completed event: userId={}, missionType={}",
                event.getUserId(), event.getMissionType(), e);
            // Don't throw exception - RocketMQ will retry automatically
            // Log the error for monitoring and troubleshooting
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
