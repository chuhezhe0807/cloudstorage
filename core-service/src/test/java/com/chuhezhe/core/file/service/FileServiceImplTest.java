package com.chuhezhe.core.file.service;

import com.chuhezhe.common.context.TenantContext;
import com.chuhezhe.core.file.entity.FileMeta;
import com.chuhezhe.core.file.mapper.FileMetaMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    @Mock
    private FileMetaMapper fileMetaMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        TenantContext.setUserId(100L);
    }

    @Test
    void createDirectorySuccess() {
        com.chuhezhe.core.file.dto.CreateDirRequest req = new com.chuhezhe.core.file.dto.CreateDirRequest();
        req.setParentId(0L);
        req.setName("test_dir");

        when(fileMetaMapper.countByNameInParent(0L, "test_dir")).thenReturn(0);

        var result = fileService.createDirectory(req);

        assertNotNull(result);
        assertTrue(result.getIsDir());
        verify(fileMetaMapper).insert(any(FileMeta.class));
    }

    @Test
    void createDirectoryNameConflictThrows() {
        var req = new com.chuhezhe.core.file.dto.CreateDirRequest();
        req.setParentId(0L);
        req.setName("existing");

        when(fileMetaMapper.countByNameInParent(0L, "existing")).thenReturn(1);

        assertThrows(com.chuhezhe.common.exception.BusinessException.class,
                () -> fileService.createDirectory(req));
    }

    @Test
    void renameSuccess() {
        FileMeta file = new FileMeta();
        file.setId(10L);
        file.setTenantId(1L);
        file.setName("old");
        file.setPath("/old/");
        file.setParentId(0L);
        file.setIsDir(false);
        file.setStatus("active");

        var req = new com.chuhezhe.core.file.dto.RenameRequest();
        req.setNewName("new");

        when(fileMetaMapper.selectById(10L)).thenReturn(file);
        when(fileMetaMapper.countByNameInParent(0L, "new")).thenReturn(0);

        var result = fileService.rename(10L, req);

        assertNotNull(result);
        verify(fileMetaMapper).updateById(argThat(f -> "new".equals(f.getName()) && "/new".equals(f.getPath())));
    }

    @Test
    void renameNameConflictThrows() {
        FileMeta file = new FileMeta();
        file.setId(10L);
        file.setTenantId(1L);
        file.setName("old");
        file.setPath("/old/");
        file.setParentId(0L);
        file.setStatus("active");

        var req = new com.chuhezhe.core.file.dto.RenameRequest();
        req.setNewName("conflict");

        when(fileMetaMapper.selectById(10L)).thenReturn(file);
        when(fileMetaMapper.countByNameInParent(0L, "conflict")).thenReturn(1);

        assertThrows(com.chuhezhe.common.exception.BusinessException.class,
                () -> fileService.rename(10L, req));
    }

    @Test
    void softDeleteSuccess() {
        FileMeta file = new FileMeta();
        file.setId(10L);
        file.setTenantId(1L);
        file.setName("test");
        file.setPath("/test/");
        file.setParentId(0L);
        file.setIsDir(false);
        file.setStatus("active");

        when(fileMetaMapper.selectById(10L)).thenReturn(file);

        fileService.softDelete(10L);

        verify(fileMetaMapper).updateById(argThat(f -> "recycled".equals(f.getStatus()) && f.getDeletedAt() != null));
    }
}
