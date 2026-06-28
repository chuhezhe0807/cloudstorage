package com.chuhezhe.user.controller;

import com.chuhezhe.common.result.Result;
import com.chuhezhe.user.dto.*;
import com.chuhezhe.user.service.AuthService;
import com.chuhezhe.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, UserController.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserService userService;

    @Test
    void registerReturnsToken() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setAccount("testuser");
        req.setPassword("password123");

        when(authService.register(any())).thenReturn(new LoginResponse("access", "refresh", 900));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh"));
    }

    @Test
    void registerShortPasswordReturns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setAccount("test");
        req.setPassword("12");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturnsToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setAccount("testuser");
        req.setPassword("password123");

        when(authService.login(any())).thenReturn(new LoginResponse("access", "refresh", 900));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access"));
    }

    @Test
    void refreshReturnsNewTokens() throws Exception {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("old-refresh");

        when(authService.refresh(any())).thenReturn(new LoginResponse("new-access", "new-refresh", 900));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"));
    }

    @Test
    void logoutSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("X-User-Id", "100"))
                .andExpect(status().isOk());

        verify(authService).logout(100L);
    }

    @Test
    void getPreferencesReturnsUserPrefs() throws Exception {
        UserPreferencesRequest prefs = new UserPreferencesRequest();
        prefs.setLocale("zh");
        prefs.setTheme("dark");

        when(userService.getPreferences(100L)).thenReturn(prefs);

        mockMvc.perform(get("/api/user/preferences")
                        .header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locale").value("zh"))
                .andExpect(jsonPath("$.data.theme").value("dark"));
    }

    @Test
    void updatePreferencesSuccess() throws Exception {
        UserPreferencesRequest req = new UserPreferencesRequest();
        req.setLocale("en");
        req.setTheme("dark");

        mockMvc.perform(put("/api/user/preferences")
                        .header("X-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(userService).updatePreferences(eq(100L), any());
    }
}
