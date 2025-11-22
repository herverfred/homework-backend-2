package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.event.LoginEvent;
import com.example.demo.exception.AuthenticationException;
import com.example.demo.repository.UserLoginRecordRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Login Service
 * Handles user login authentication and event publishing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LoginService {

    private final UserRepository userRepository;
    private final UserLoginRecordRepository loginRecordRepository;
    private RocketMQTemplate rocketMQTemplate;

    // RocketMQ topic for login events
    public static final String LOGIN_EVENT_TOPIC = "mission-login-event";

    @Autowired(required = false)
    public void setRocketMQTemplate(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * Authenticate user login and publish event
     *
     * @param username Username
     * @param password Password
     * @param loginDate Login date (optional, null means today)
     * @return User information
     * @throws RuntimeException If username or password is incorrect
     */
    public User authenticateAndLogin(String username, String password, Date loginDate) {
        log.info("Authenticating login for username: {}", username);

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> {
                log.warn("Login failed: username not found: {}", username);
                return new AuthenticationException("Invalid username or password");
            });

        if (password == null || !password.equals(user.getPassword())) {
            log.warn("Login failed: incorrect password for username: {}", username);
            throw new AuthenticationException("Invalid username or password");
        }

        // If no date specified, use today
        Date effectiveDate = loginDate != null ? loginDate : Date.valueOf(LocalDate.now());

        // Publish LoginEvent
        publishLoginEvent(user.getId(), effectiveDate);

        log.info("Login successful for user: {} (userId: {}), date: {}", username, user.getId(), effectiveDate);
        return user;
    }

    /**
     * Publish login event to RocketMQ
     * Event will be processed asynchronously by LoginEventConsumer (init missions, record login, check missions)
     *
     * @param userId User ID
     * @param loginDate Login date
     */
    private void publishLoginEvent(Long userId, Date loginDate) {
        if (rocketMQTemplate == null) {
            log.warn("RocketMQTemplate not available, skipping event publishing");
            return;
        }

        LoginEvent event = LoginEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .userId(userId)
            .loginDate(loginDate)
            .build();

        rocketMQTemplate.asyncSend(LOGIN_EVENT_TOPIC, event, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.debug("Login event sent successfully for userId: {}, msgId: {}",
                    event.getUserId(), sendResult.getMsgId());
            }

            @Override
            public void onException(Throwable e) {
                log.error("Failed to send login event for userId: {}", event.getUserId(), e);
            }
        });
        log.debug("Async login event published for userId: {} to topic: {}", userId, LOGIN_EVENT_TOPIC);
    }

    /**
     * Record login date for user
     * Uses INSERT IGNORE to handle concurrent inserts gracefully
     *
     * @param userId the user ID
     * @param loginDate the login date
     */
    @Transactional
    public void recordLogin(Long userId, Date loginDate) {
        loginRecordRepository.insertIgnore(userId, loginDate);
        log.debug("Login date recorded for user: {}, date: {}", userId, loginDate);
    }
}
