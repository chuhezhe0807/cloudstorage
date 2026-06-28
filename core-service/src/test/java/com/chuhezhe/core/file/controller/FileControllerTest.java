package com.chuhezhe.core.file.controller;

import com.chuhezhe.core.file.dto.*;
import com.chuhezhe.core.file.service.FileService;
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

@WebMvcTest(FileController.class)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FileService fileService;

    @Test
    void createDirectoryReturnsVo() throws Exception {
        CreateDirRequest req = new CreateDirRequest();
        req.setParentId(0L);
        req.setName("docs");

        FileMetaVO vo = new FileMetaVO();
        vo.setId(1L);
        vo.setName("docs");
        vo.setIsDir(true);
        vo.setPath("/docs/");

        when(fileService.createDirectory(any())).thenReturn(vo);

        mockMvc.perform(post("/api/files/mkdir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("docs"))
                .andExpect(jsonPath("$.data.isDir").value(true));
    }

    @Test
    void listFilesReturnsList() throws Exception {
        FileMetaVO vo = new FileMetaVO();
        vo.setId(1L);
        vo.setName("file.txt");

        when(fileService.listFiles(any())).thenReturn(List.of(vo));

        mockMvc.perform(get("/api/files").param("parentId", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("file.txt"));
    }

    @Test
    void renameReturnsUpdatedVo() throws Exception {
        RenameRequest req = new RenameRequest();
        req.setNewName("renamed.txt");

        FileMetaVO vo = new FileMetaVO();
        vo.setId(1L);
        vo.setName("renamed.txt");

        when(fileService.rename(eq(1L), any())).thenReturn(vo);

        mockMvc.perform(patch("/api/files/1/rename")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("renamed.txt"));
    }

    @Test
    void softDeleteReturnsOk() throws Exception {
        mockMvc.perform(delete("/api/files/1"))
                .andExpect(status().isOk());
    }

    @Test
    void restoreReturnsOk() throws Exception {
        mockMvc.perform(put("/api/files/1/restore"))
                .andExpect(status().isOk());
    }
}
