package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.entity.Game;
import com.example.demo.entity.Mission;
import com.example.demo.entity.MissionReward;
import com.example.demo.entity.User;
import com.example.demo.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mission Center REST Controller
 * Provides APIs for user activity tracking and mission progress
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MissionController {

    private final LoginService loginService;
    private final GameLaunchService gameLaunchService;
    private final GamePlayService gamePlayService;
    private final MissionService missionService;
    private final RewardDistributionService rewardDistributionService;
    private final GameService gameService;

    /**
     * POST /api/login
     * User login authentication
     * Validates username and password, then returns user information
     *
     * @param request Login request containing username, password, and optional loginDate
     * @return User information on successful login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/login - username: {}, loginDate: {}", request.getUsername(), request.getLoginDate());

        // Parse login date (optional, for test simulation)
        Date loginDate = null;
        if (request.getLoginDate() != null && !request.getLoginDate().isBlank()) {
            loginDate = Date.valueOf(LocalDate.parse(request.getLoginDate()));
        }

        User user = loginService.authenticateAndLogin(
            request.getUsername(),
            request.getPassword(),
            loginDate
        );

        LoginResponse loginResponse = LoginResponse.builder()
            .userId(user.getId())
            .username(user.getUsername())
            .points(user.getPoints())
            .build();

        return ResponseEntity.ok(ApiResponse.<LoginResponse>builder()
            .success(true)
            .message("Login successful")
            .data(loginResponse)
            .build());
    }

    /**
     * POST /api/launchGame
     * Record game launch event
     *
     * @param request Game launch request
     * @return Success response
     */
    @PostMapping("/launchGame")
    public ResponseEntity<ApiResponse<String>> launchGame(@Valid @RequestBody LaunchGameRequest request) {
        log.info("POST /api/launchGame - userId: {}, gameId: {}",
            request.getUserId(), request.getGameId());

        gameLaunchService.handleLaunch(request.getUserId(), request.getGameId());

        return ResponseEntity.ok(ApiResponse.<String>builder()
            .success(true)
            .message("Game launch recorded successfully")
            .data(String.format("User %d launched game %d",
                request.getUserId(), request.getGameId()))
            .build());
    }

    /**
     * POST /api/play
     * Record game play event
     * Backend randomly generates score (0-1000)
     *
     * @param request Game play request
     * @return Success response with generated score
     */
    @PostMapping("/play")
    public ResponseEntity<ApiResponse<PlayResponse>> play(@Valid @RequestBody PlayRequest request) {
        log.info("POST /api/play - userId: {}, gameId: {}",
            request.getUserId(), request.getGameId());

        // Handle game play (validate + generate random score + publish event)
        // Exceptions are handled uniformly by GlobalExceptionHandler
        Integer score = gamePlayService.handlePlay(request.getUserId(), request.getGameId());

        // Build response
        PlayResponse playResponse = PlayResponse.builder()
            .userId(request.getUserId())
            .gameId(request.getGameId())
            .score(score)
            .build();

        return ResponseEntity.ok(ApiResponse.<PlayResponse>builder()
            .success(true)
            .message("Game play recorded successfully")
            .data(playResponse)
            .build());
    }

    /**
     * GET /api/missions
     * Get user mission progress
     *
     * @param userId User ID
     * @return Mission progress details
     */
    @GetMapping("/missions")
    public ResponseEntity<ApiResponse<MissionProgressDTO>> getMissions(
            @RequestParam() Long userId) {
        log.info("GET /api/missions - userId: {}", userId);

        // Get mission progress
        // Exceptions are handled uniformly by GlobalExceptionHandler
        List<Mission> missions = missionService.getMissionProgress(userId);

        // Convert to DTO
        MissionProgressDTO progressDTO = convertToMissionProgressDTO(userId, missions);

        return ResponseEntity.ok(ApiResponse.<MissionProgressDTO>builder()
            .success(true)
            .message("Mission progress retrieved successfully")
            .data(progressDTO)
            .build());
    }

    /**
     * GET /api/rewards
     * Get user reward history
     *
     * @param userId User ID
     * @return Reward history list
     */
    @GetMapping("/rewards")
    public ResponseEntity<ApiResponse<List<RewardHistoryDTO>>> getRewards(
            @RequestParam() Long userId) {
        log.info("GET /api/rewards - userId: {}", userId);

        // Get reward history
        // Exceptions are handled uniformly by GlobalExceptionHandler
        List<MissionReward> rewards = rewardDistributionService.getRewardHistory(userId);

        // Convert to DTO
        List<RewardHistoryDTO> rewardDTOs = rewards.stream()
            .map(this::convertToRewardHistoryDTO)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.<List<RewardHistoryDTO>>builder()
            .success(true)
            .message("Reward history retrieved successfully")
            .data(rewardDTOs)
            .build());
    }

    /**
     * GET /api/games
     * Get all available games list
     *
     * @return Games list
     */
    @GetMapping("/games")
    public ResponseEntity<ApiResponse<List<GameDTO>>> getGames() {
        log.info("GET /api/games");

        // Get all games
        // Exceptions are handled uniformly by GlobalExceptionHandler
        List<Game> games = gameService.getAllGames();

        // Convert to DTO
        List<GameDTO> gameDTOs = games.stream()
            .map(this::convertToGameDTO)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.<List<GameDTO>>builder()
            .success(true)
            .message("Games retrieved successfully")
            .data(gameDTOs)
            .build());
    }

    /**
     * Convert Mission entities to MissionProgressDTO
     */
    private MissionProgressDTO convertToMissionProgressDTO(Long userId, List<Mission> missions) {
        MissionProgressDTO.MissionDetail loginMission = null;
        MissionProgressDTO.MissionDetail launchMission = null;
        MissionProgressDTO.MissionDetail playMission = null;

        for (Mission mission : missions) {
            switch (mission.getMissionType()) {
                case MissionInitService.MISSION_TYPE_LOGIN:
                    int consecutiveDays = missionService.calculateConsecutiveLoginDays(userId);
                    Map<String, Object> loginExtra = new HashMap<>();
                    loginExtra.put("consecutiveDays", consecutiveDays);

                    loginMission = MissionProgressDTO.MissionDetail.builder()
                        .type(mission.getMissionType())
                        .description("Log in for 3 consecutive days")
                        .currentProgress(consecutiveDays)
                        .targetProgress(3)
                        .progressText(consecutiveDays + " / 3 days")
                        .completed(mission.getIsCompleted())
                        .completedAt(mission.getCompletedAt())
                        .extraInfo(loginExtra)
                        .build();
                    break;

                case MissionInitService.MISSION_TYPE_LAUNCH:
                    long launchedGamesCount = missionService.getUniqueGamesLaunchedCount(userId);
                    Map<String, Object> launchExtra = new HashMap<>();
                    launchExtra.put("uniqueGamesLaunched", launchedGamesCount);

                    launchMission = MissionProgressDTO.MissionDetail.builder()
                        .type(mission.getMissionType())
                        .description("Launch at least 3 different games")
                        .currentProgress((int) launchedGamesCount)
                        .targetProgress(3)
                        .progressText(launchedGamesCount + " / 3 games")
                        .completed(mission.getIsCompleted())
                        .completedAt(mission.getCompletedAt())
                        .extraInfo(launchExtra)
                        .build();
                    break;

                case MissionInitService.MISSION_TYPE_PLAY:
                    var playStats = missionService.getGamePlayStats(userId);
                    Map<String, Object> playExtra = new HashMap<>();
                    playExtra.put("playCount", playStats.getCount());
                    playExtra.put("totalScore", playStats.getTotalScore());

                    playMission = MissionProgressDTO.MissionDetail.builder()
                        .type(mission.getMissionType())
                        .description("Play at least 3 game sessions with combined score > 1,000 points")
                        .currentProgress(playStats.getCount().intValue())
                        .targetProgress(3)
                        .progressText(playStats.getCount() + " plays, " +
                            playStats.getTotalScore() + " / 1,000 points")
                        .completed(mission.getIsCompleted())
                        .completedAt(mission.getCompletedAt())
                        .extraInfo(playExtra)
                        .build();
                    break;
            }
        }

        return MissionProgressDTO.builder()
            .userId(userId)
            .loginMission(loginMission)
            .launchMission(launchMission)
            .playMission(playMission)
            .build();
    }

    /**
     * Convert MissionReward entity to RewardHistoryDTO
     */
    private RewardHistoryDTO convertToRewardHistoryDTO(MissionReward reward) {
        return RewardHistoryDTO.builder()
            .period(reward.getRewardPeriod())
            .points(reward.getPoints())
            .distributedAt(reward.getDistributedAt())
            .rewardType(reward.getRewardType())
            .build();
    }

    /**
     * Convert Game entity to GameDTO
     */
    private GameDTO convertToGameDTO(Game game) {
        return GameDTO.builder()
            .id(game.getId())
            .name(game.getName())
            .description(game.getDescription())
            .build();
    }
}
