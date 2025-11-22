package com.example.demo.service;

import com.example.demo.entity.Game;
import com.example.demo.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Game Service with Redis Caching
 * Manages game data with caching for frequently accessed game list
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;

    /**
     * Get all games (with Redis caching)
     * Cache key: "games"
     * Games are rarely modified, so this cache can be long-lived
     *
     * @return list of all games
     */
    @Cacheable(value = "games")
    public List<Game> getAllGames() {
        log.info("Fetching all games (from DB)");

        try {
            List<Game> games = gameRepository.findAll();
            log.debug("Found {} games", games.size());
            return games;

        } catch (Exception e) {
            log.error("Failed to fetch games", e);
            throw new RuntimeException("Failed to fetch games", e);
        }
    }

    /**
     * Check if a game exists
     *
     * @param gameId the game ID
     * @return true if game exists
     */
    public boolean gameExists(Long gameId) {
        try {
            boolean exists = gameRepository.existsById(gameId);
            log.debug("Game {} exists: {}", gameId, exists);
            return exists;

        } catch (Exception e) {
            log.error("Failed to check if game exists: {}", gameId, e);
            return false;
        }
    }
}
