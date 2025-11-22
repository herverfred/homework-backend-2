package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.entity.Game;
import com.example.demo.entity.Mission;
import com.example.demo.entity.MissionReward;
import com.example.demo.entity.User;
import com.example.demo.exception.AuthenticationException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.GamePlayRecordRepository;
import com.example.demo.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MissionController Unit Tests
 * Uses MockMvc to test REST API endpoints
 */
@WebMvcTest(MissionController.class)
class MissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LoginService loginService;

    @MockBean
    private GameLaunchService gameLaunchService;

    @MockBean
    private GamePlayService gamePlayService;

    @MockBean
    private MissionService missionService;

    @MockBean
    private RewardDistributionService rewardDistributionService;

    @MockBean
    private GameService gameService;

    private User testUser;
    private Game testGame;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L)
            .username("test_user")
            .password("password123")
            .points(100)
            .build();

        testGame = Game.builder()
            .id(1L)
            .name("Test Game")
            .description("A test game")
            .build();
    }

    // ==================== Login API Tests ====================

    @Nested
    @DisplayName("POST /api/login")
    class LoginTests {

        @Test
        @DisplayName("Login successful")
        void login_Success() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("test_user");
            request.setPassword("password123");

            when(loginService.authenticateAndLogin(eq("test_user"), eq("password123"), isNull()))
                .thenReturn(testUser);

            mockMvc.perform(post("/api/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.username").value("test_user"))
                .andExpect(jsonPath("$.data.points").value(100));
        }

        @Test
        @DisplayName("Login successful - with specified date")
        void login_WithLoginDate_Success() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("test_user");
            request.setPassword("password123");
            request.setLoginDate("2025-11-21");

            Date expectedDate = Date.valueOf(LocalDate.parse("2025-11-21"));
            when(loginService.authenticateAndLogin(eq("test_user"), eq("password123"), eq(expectedDate)))
                .thenReturn(testUser);

            mockMvc.perform(post("/api/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(1));
        }

        @Test
        @DisplayName("Login failed - invalid username")
        void login_InvalidUsername_Fail() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("wrong_user");
            request.setPassword("password123");

            when(loginService.authenticateAndLogin(eq("wrong_user"), eq("password123"), isNull()))
                .thenThrow(new AuthenticationException("Invalid username or password"));

            mockMvc.perform(post("/api/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Login failed - invalid password")
        void login_InvalidPassword_Fail() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("test_user");
            request.setPassword("wrong_password");

            when(loginService.authenticateAndLogin(eq("test_user"), eq("wrong_password"), isNull()))
                .thenThrow(new AuthenticationException("Invalid username or password"));

            mockMvc.perform(post("/api/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Login failed - missing username")
        void login_MissingUsername_Fail() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setPassword("password123");

            mockMvc.perform(post("/api/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
    }

    // ==================== Launch Game API Tests ====================

    @Nested
    @DisplayName("POST /api/launchGame")
    class LaunchGameTests {

        @Test
        @DisplayName("Launch game successful")
        void launchGame_Success() throws Exception {
            LaunchGameRequest request = new LaunchGameRequest();
            request.setUserId(1L);
            request.setGameId(1L);

            mockMvc.perform(post("/api/launchGame")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Game launch recorded successfully"));
        }

        @Test
        @DisplayName("Launch game failed - user not found")
        void launchGame_UserNotFound_Fail() throws Exception {
            LaunchGameRequest request = new LaunchGameRequest();
            request.setUserId(999L);
            request.setGameId(1L);

            doThrow(new ResourceNotFoundException("User not found: 999"))
                .when(gameLaunchService).handleLaunch(eq(999L), eq(1L));

            mockMvc.perform(post("/api/launchGame")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Launch game failed - game not found")
        void launchGame_GameNotFound_Fail() throws Exception {
            LaunchGameRequest request = new LaunchGameRequest();
            request.setUserId(1L);
            request.setGameId(999L);

            doThrow(new ResourceNotFoundException("Game not found: 999"))
                .when(gameLaunchService).handleLaunch(eq(1L), eq(999L));

            mockMvc.perform(post("/api/launchGame")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ==================== Play Game API Tests ====================

    @Nested
    @DisplayName("POST /api/play")
    class PlayGameTests {

        @Test
        @DisplayName("Game play successful")
        void play_Success() throws Exception {
            PlayRequest request = new PlayRequest();
            request.setUserId(1L);
            request.setGameId(1L);

            when(gamePlayService.handlePlay(eq(1L), eq(1L))).thenReturn(500);

            mockMvc.perform(post("/api/play")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Game play recorded successfully"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.gameId").value(1))
                .andExpect(jsonPath("$.data.score").value(500));
        }

        @Test
        @DisplayName("Game play failed - user not found")
        void play_UserNotFound_Fail() throws Exception {
            PlayRequest request = new PlayRequest();
            request.setUserId(999L);
            request.setGameId(1L);

            when(gamePlayService.handlePlay(eq(999L), eq(1L)))
                .thenThrow(new ResourceNotFoundException("User not found: 999"));

            mockMvc.perform(post("/api/play")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Game play failed - game not found")
        void play_GameNotFound_Fail() throws Exception {
            PlayRequest request = new PlayRequest();
            request.setUserId(1L);
            request.setGameId(999L);

            when(gamePlayService.handlePlay(eq(1L), eq(999L)))
                .thenThrow(new ResourceNotFoundException("Game not found: 999"));

            mockMvc.perform(post("/api/play")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ==================== Get Missions API Tests ====================

    @Nested
    @DisplayName("GET /api/missions")
    class GetMissionsTests {

        @Test
        @DisplayName("Get mission progress successful")
        void getMissions_Success() throws Exception {
            List<Mission> missions = Arrays.asList(
                Mission.builder()
                    .id(1L)
                    .userId(1L)
                    .missionType("CONSECUTIVE_LOGIN_3_DAYS")
                    .isCompleted(false)
                    .build(),
                Mission.builder()
                    .id(2L)
                    .userId(1L)
                    .missionType("LAUNCH_3_DIFFERENT_GAMES")
                    .isCompleted(false)
                    .build(),
                Mission.builder()
                    .id(3L)
                    .userId(1L)
                    .missionType("PLAY_3_GAMES_TOTAL_SCORE_1000")
                    .isCompleted(false)
                    .build()
            );

            when(missionService.getMissionProgress(1L)).thenReturn(missions);
            when(missionService.calculateConsecutiveLoginDays(1L)).thenReturn(2);
            when(missionService.getUniqueGamesLaunchedCount(1L)).thenReturn(1L);
            when(missionService.getGamePlayStats(1L)).thenReturn(
                new GamePlayRecordRepository.GamePlayStats() {
                    @Override
                    public Long getCount() { return 2L; }
                    @Override
                    public Long getTotalScore() { return 800L; }
                }
            );

            mockMvc.perform(get("/api/missions")
                    .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.loginMission").exists())
                .andExpect(jsonPath("$.data.launchMission").exists())
                .andExpect(jsonPath("$.data.playMission").exists());
        }

        @Test
        @DisplayName("Get mission progress failed - missing userId")
        void getMissions_MissingUserId_Fail() throws Exception {
            mockMvc.perform(get("/api/missions"))
                .andExpect(status().isInternalServerError());
        }
    }

    // ==================== Get Rewards API Tests ====================

    @Nested
    @DisplayName("GET /api/rewards")
    class GetRewardsTests {

        @Test
        @DisplayName("Get reward history successful")
        void getRewards_Success() throws Exception {
            List<MissionReward> rewards = Arrays.asList(
                MissionReward.builder()
                    .id(1L)
                    .userId(1L)
                    .rewardType("MISSION_30_DAY")
                    .rewardPeriod("2025-11")
                    .points(777)
                    .build()
            );

            when(rewardDistributionService.getRewardHistory(1L)).thenReturn(rewards);

            mockMvc.perform(get("/api/rewards")
                    .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].points").value(777))
                .andExpect(jsonPath("$.data[0].period").value("2025-11"));
        }

        @Test
        @DisplayName("Get reward history successful - no rewards")
        void getRewards_Empty_Success() throws Exception {
            when(rewardDistributionService.getRewardHistory(1L)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/rewards")
                    .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ==================== Get Games API Tests ====================

    @Nested
    @DisplayName("GET /api/games")
    class GetGamesTests {

        @Test
        @DisplayName("Get games list successful")
        void getGames_Success() throws Exception {
            List<Game> games = Arrays.asList(
                Game.builder().id(1L).name("Pharaoh Treasure").description("Egyptian themed slot game").build(),
                Game.builder().id(2L).name("Ocean Quest").description("Ocean adventure slot game").build(),
                Game.builder().id(3L).name("Nordic Gods").description("Norse mythology slot game").build()
            );

            when(gameService.getAllGames()).thenReturn(games);

            mockMvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("Pharaoh Treasure"))
                .andExpect(jsonPath("$.data[1].name").value("Ocean Quest"))
                .andExpect(jsonPath("$.data[2].name").value("Nordic Gods"));
        }
    }
}
