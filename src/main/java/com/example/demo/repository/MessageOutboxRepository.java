package com.example.demo.repository;

import com.example.demo.entity.MessageOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * Message Outbox Repository
 * For querying and managing failed messages pending retry
 */
@Repository
public interface MessageOutboxRepository extends JpaRepository<MessageOutbox, Long> {

    /**
     * Find all pending messages that are ready for retry
     *
     * @param status the message status (PENDING)
     * @param now current time to compare with next_retry_at
     * @return list of messages ready for retry
     */
    List<MessageOutbox> findByStatusAndNextRetryAtLessThanEqual(String status, Date now);
}
