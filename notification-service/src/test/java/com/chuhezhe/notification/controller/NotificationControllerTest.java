package com.chuhezhe.notification.controller;

import com.chuhezhe.common.dto.PageResult;
import com.chuhezhe.notification.dto.NotificationVO;
import com.chuhezhe.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    void listNotificationsReturnsPage() throws Exception {
        NotificationVO vo = new NotificationVO();
        vo.setId(1L);
        vo.setType("upload.completed");
        vo.setPayload("{\"fileName\":\"test.txt\"}");
        vo.setRead(false);

        when(notificationService.listNotifications(eq(100L), any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(vo), 1, 1, 20));

        mockMvc.perform(get("/api/notifications")
                        .header("X-User-Id", "100")
                        .param("unread", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].type").value("upload.completed"));
    }

    @Test
    void markAsReadSuccess() throws Exception {
        mockMvc.perform(put("/api/notifications/1/read")
                        .header("X-User-Id", "100"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteBatchSuccess() throws Exception {
        mockMvc.perform(delete("/api/notifications")
                        .header("X-User-Id", "100")
                        .param("ids", "1", "2", "3"))
                .andExpect(status().isOk());
    }
}
