package com.chuhezhe.core.share.controller;

import com.chuhezhe.core.share.dto.*;
import com.chuhezhe.core.share.service.ShareService;
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

@WebMvcTest(ShareController.class)
class ShareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShareService shareService;

    @Test
    void createShareReturnsVo() throws Exception {
        CreateShareRequest req = new CreateShareRequest();
        req.setFileId(1L);

        ShareVO vo = new ShareVO();
        vo.setId(1L);
        vo.setCode("abc123");

        when(shareService.create(any())).thenReturn(vo);

        mockMvc.perform(post("/api/shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("abc123"));
    }

    @Test
    void accessShareReturnsDownloadUrl() throws Exception {
        ShareAccessResponse resp = new ShareAccessResponse();
        resp.setFileId(1L);
        resp.setFileName("test.txt");
        resp.setFileSize(1024);
        resp.setDir(false);
        resp.setDownloadUrl("http://minio/dl");

        when(shareService.access(eq("abc123"), any())).thenReturn(resp);

        mockMvc.perform(post("/api/shares/abc123/access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("test.txt"));
    }

    @Test
    void shareInfoReturnsResponse() throws Exception {
        ShareInfoResponse resp = new ShareInfoResponse("test.txt", 1024, false, true, null, 10, 1);

        when(shareService.getShareInfo("abc123")).thenReturn(resp);

        mockMvc.perform(get("/api/shares/abc123/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("test.txt"))
                .andExpect(jsonPath("$.data.fileSize").value(1024))
                .andExpect(jsonPath("$.data.hasPassword").value(true));
    }

    @Test
    void downloadShareReturnsZip() throws Exception {
        when(shareService.downloadFiles(eq("abc123"), anyList())).thenReturn(new byte[]{1, 2, 3});

        ShareDownloadRequest req = new ShareDownloadRequest();
        req.setFileIds(List.of(1L, 2L));

        mockMvc.perform(post("/api/shares/abc123/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".zip")));
    }

    @Test
    void listMySharesReturnsList() throws Exception {
        ShareVO vo = new ShareVO();
        vo.setId(1L);
        vo.setCode("abc123");

        when(shareService.listMyShares()).thenReturn(List.of(vo));

        mockMvc.perform(get("/api/shares"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("abc123"));
    }

    @Test
    void cancelShareSuccess() throws Exception {
        mockMvc.perform(delete("/api/shares/1"))
                .andExpect(status().isOk());
    }
}
