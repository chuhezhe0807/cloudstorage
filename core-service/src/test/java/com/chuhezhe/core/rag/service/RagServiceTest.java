package com.chuhezhe.core.rag.service;

import com.chuhezhe.common.context.TenantContext;
import com.chuhezhe.common.spi.EmbeddingProvider;
import com.chuhezhe.common.spi.LangfuseTraceHelper;
import com.chuhezhe.common.spi.LlmClient;
import com.chuhezhe.common.exception.BusinessException;
import com.chuhezhe.core.file.entity.FileMeta;
import com.chuhezhe.core.file.mapper.FileMetaMapper;
import com.chuhezhe.core.kb.entity.KbChunk;
import com.chuhezhe.core.kb.entity.KnowledgeBase;
import com.chuhezhe.core.kb.mapper.KbChunkMapper;
import com.chuhezhe.core.kb.mapper.KnowledgeBaseMapper;
import com.chuhezhe.core.rag.dto.ChatRequest;
import com.chuhezhe.core.rag.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock
    private KbChunkMapper kbChunkMapper;
    @Mock
    private FileMetaMapper fileMetaMapper;
    @Mock
    private EmbeddingProvider embeddingProvider;
    @Mock
    private LlmClient llmClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ListOperations<String, String> listOps;
    @Mock
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private LangfuseTraceHelper langfuseTraceHelper;

    @InjectMocks
    private RagService ragService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void chatKbNotFound() {
        when(knowledgeBaseMapper.selectById(999L)).thenReturn(null);

        ChatRequest req = new ChatRequest();
        req.setKbId(999L);
        req.setMessage("hello");
        assertThrows(BusinessException.class, () -> ragService.chat(req));
    }

    @Test
    void chatKbNotReady() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setTenantId(1L);
        kb.setStatus("processing");
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);

        ChatRequest req = new ChatRequest();
        req.setKbId(1L);
        req.setMessage("hello");
        assertThrows(BusinessException.class, () -> ragService.chat(req));
    }

    @Test
    void chatCrossTenantNotFound() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setTenantId(2L);
        kb.setStatus("ready");
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(kb);

        ChatRequest req = new ChatRequest();
        req.setKbId(1L);
        req.setMessage("hello");
        assertThrows(BusinessException.class, () -> ragService.chat(req));
    }
}
