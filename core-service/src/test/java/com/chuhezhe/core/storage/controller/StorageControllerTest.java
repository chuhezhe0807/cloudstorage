package com.chuhezhe.core.storage.controller;

import com.chuhezhe.core.file.mapper.FileMetaMapper;
import com.chuhezhe.core.storage.dto.*;
import com.chuhezhe.core.storage.service.MinioService;
import com.chuhezhe.core.storage.service.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StorageController.class)
class StorageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StorageService storageService;

    @MockBean
    private MinioService minioService;

    @MockBean
    private FileMetaMapper fileMetaMapper;

    @Test
    void checkHashHitReturns200() throws Exception {
        CheckHashRequest req = new CheckHashRequest();
        req.setHash("abc123");
        req.setFileName("test.txt");
        req.setFileSize(1024);

        when(storageService.checkHash(any())).thenReturn(new FileUploadResponse(1L, "test.txt", 1024, true));

        mockMvc.perform(post("/api/storage/check-hash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instantTransfer").value(true));
    }

    @Test
    void checkHashMissReturns404() throws Exception {
        CheckHashRequest req = new CheckHashRequest();
        req.setHash("abc123");
        req.setFileName("test.txt");
        req.setFileSize(1024);

        when(storageService.checkHash(any())).thenReturn(null);

        mockMvc.perform(post("/api/storage/check-hash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void initUploadReturnsUrls() throws Exception {
        UploadInitRequest req = new UploadInitRequest();
        req.setFileName("test.txt");
        req.setTotalSize(10 * 1024 * 1024);

        UploadInitResponse resp = new UploadInitResponse("upload-1", 2, List.of());
        when(storageService.initUpload(any())).thenReturn(resp);

        mockMvc.perform(post("/api/storage/upload/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadId").value("upload-1"))
                .andExpect(jsonPath("$.data.totalChunks").value(2));
    }

    @Test
    void getProgressReturnsStatus() throws Exception {
        UploadProgressResponse resp = new UploadProgressResponse();
        resp.setUploadId("upload-1");
        resp.setTotalChunks(3);
        resp.setUploadedChunks(List.of(0));
        resp.setStatus("uploading");

        when(storageService.getUploadProgress("upload-1")).thenReturn(resp);

        mockMvc.perform(get("/api/storage/upload/upload-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("uploading"));
    }

    @Test
    void completeUploadReturnsFileInfo() throws Exception {
        when(storageService.completeUpload("upload-1"))
                .thenReturn(new FileUploadResponse(1L, "test.txt", 1024, false));

        mockMvc.perform(post("/api/storage/upload/upload-1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("test.txt"));
    }
}
