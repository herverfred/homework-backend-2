package com.example.demo.repository;

import com.example.demo.entity.UserLoginRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface UserLoginRecordRepository extends JpaRepository<UserLoginRecord, Long> {

    /**
     * Find the most recent 10 login records for a user, ordered by login date descending
     *
     * @param userId the user ID
     * @return list of up to 10 most recent login records
     */
    List<UserLoginRecord> findTop10ByUserIdOrderByLoginDateDesc(Long userId);

    /**
     * Check if a login record exists for a user on a specific date
     *
     * @param userId the user ID
     * @param loginDate the login date
     * @return true if exists
     */
    boolean existsByUserIdAndLoginDate(Long userId, Date loginDate);

    /**
     * Insert login record, ignore if duplicate (MySQL INSERT IGNORE)
     *
     * @param userId the user ID
     * @param loginDate the login date
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO user_login_records (user_id, login_date, created_at) VALUES (:userId, :loginDate, NOW())", nativeQuery = true)
    void insertIgnore(@Param("userId") Long userId, @Param("loginDate") Date loginDate);
}
