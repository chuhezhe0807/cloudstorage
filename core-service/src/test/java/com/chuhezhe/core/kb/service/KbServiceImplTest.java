package com.chuhezhe.core.kb.service;

import com.chuhezhe.common.context.TenantContext;
import com.chuhezhe.core.file.entity.FileMeta;
import com.chuhezhe.core.file.mapper.FileMetaMapper;
import com.chuhezhe.core.kb.entity.KnowledgeBase;
import com.chuhezhe.core.kb.mapper.KnowledgeBaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KbServiceImplTest {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private FileMetaMapper fileMetaMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private KbServiceImpl kbService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        TenantContext.setUserId(100L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void triggerVectorizeNewFolder() {
        FileMeta folder = new FileMeta();
        folder.setId(10L);
        folder.setIsDir(true);
        folder.setName("docs");
        when(fileMetaMapper.selectById(10L)).thenReturn(folder);
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(null);
        when(fileMetaMapper.listByParent(any())).thenReturn(List.of());
        when(knowledgeBaseMapper.insert(any(KnowledgeBase.class))).thenReturn(1);
        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString());

        Long kbId = kbService.triggerVectorize(10L);
        assertNotNull(kbId);
        verify(knowledgeBaseMapper).insert(any(KnowledgeBase.class));
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString());
    }

    @Test
    void triggerVectorizeReuseExistingProcessing() {
        FileMeta folder = new FileMeta();
        folder.setId(10L);
        folder.setIsDir(true);
        folder.setName("docs");
        KnowledgeBase existing = new KnowledgeBase();
        existing.setId(100L);
        existing.setStatus("processing");
        when(fileMetaMapper.selectById(10L)).thenReturn(folder);
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(existing);

        Long kbId = kbService.triggerVectorize(10L);
        assertEquals(100L, kbId);
        verify(knowledgeBaseMapper, never()).insert(any(KnowledgeBase.class));
    }

    @Test
    void triggerVectorizeNotAFolder() {
        FileMeta file = new FileMeta();
        file.setId(10L);
        file.setIsDir(false);
        when(fileMetaMapper.selectById(10L)).thenReturn(file);

        assertThrows(Exception.class, () -> kbService.triggerVectorize(10L));
    }
}
