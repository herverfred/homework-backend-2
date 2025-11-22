package com.example.demo.repository;

import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by username
     *
     * @param username the username
     * @return Optional containing the user if found
     */
    Optional<User> findByUsername(String username);

    /**
     * Add points to user's account
     *
     * @param userId the user ID
     * @param points the points to add
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE User u SET u.points = u.points + :points, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :userId")
    int addPoints(@Param("userId") Long userId, @Param("points") Integer points);
}
