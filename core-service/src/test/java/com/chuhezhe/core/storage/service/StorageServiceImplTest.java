package com.chuhezhe.core.storage.service;

import com.chuhezhe.common.context.TenantContext;
import com.chuhezhe.core.file.entity.FileContent;
import com.chuhezhe.core.file.mapper.FileContentMapper;
import com.chuhezhe.core.file.mapper.FileMetaMapper;
import com.chuhezhe.core.storage.dto.CheckHashRequest;
import com.chuhezhe.core.storage.dto.FileUploadResponse;
import com.chuhezhe.core.storage.mapper.OutboxEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceImplTest {

    @Mock
    private MinioService minioService;

    @Mock
    private FileMetaMapper fileMetaMapper;

    @Mock
    private FileContentMapper fileContentMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @Mock
    private SetOperations<String, String> setOps;

    @Mock
    private OutboxEventMapper outboxEventMapper;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private StorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        TenantContext.setUserId(100L);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
    }

    @Test
    void checkHashHitReturnsResponse() {
        CheckHashRequest req = new CheckHashRequest();
        req.setHash("abc123");
        req.setFileName("test.txt");
        req.setFileSize(1024);

        FileContent existing = new FileContent();
        existing.setId(1L);
        existing.setHash("abc123");
        existing.setRefCount(1);
        existing.setStorageKey("tenant/1/obj");

        when(fileContentMapper.findByHash("abc123")).thenReturn(existing);

        FileUploadResponse resp = storageService.checkHash(req);

        assertNotNull(resp);
        assertTrue(resp.isInstantTransfer());
        verify(fileContentMapper).updateById(argThat(fc -> fc.getRefCount() == 2));
        verify(fileMetaMapper).insert(any());
    }

    @Test
    void checkHashMissReturnsNull() {
        CheckHashRequest req = new CheckHashRequest();
        req.setHash("abc123");
        req.setFileName("test.txt");
        req.setFileSize(1024);

        when(fileContentMapper.findByHash("abc123")).thenReturn(null);

        FileUploadResponse resp = storageService.checkHash(req);

        assertNull(resp);
    }

    @Test
    void checkHashInvalidFilenameThrows() {
        CheckHashRequest req = new CheckHashRequest();
        req.setHash("abc123");
        req.setFileName("../etc/passwd");
        req.setFileSize(1024);

        assertThrows(IllegalArgumentException.class, () -> storageService.checkHash(req));
    }

    @Test
    void checkHashInvalidExtensionThrows() {
        CheckHashRequest req = new CheckHashRequest();
        req.setHash("abc123");
        req.setFileName("virus.exe");
        req.setFileSize(1024);

        assertThrows(IllegalArgumentException.class, () -> storageService.checkHash(req));
    }

    @Test
    void initUploadReturnsUrls() {
        com.chuhezhe.core.storage.dto.UploadInitRequest req =
                new com.chuhezhe.core.storage.dto.UploadInitRequest();
        req.setFileName("test.txt");
        req.setTotalSize(10 * 1024 * 1024); // 10MB
        req.setChunkSize(5 * 1024 * 1024);

        when(minioService.getTenantPrefix()).thenReturn("tenant/1/");
        when(minioService.presignedPutUrl(anyString(), anyInt())).thenReturn("http://minio/presigned");
        when(fileMetaMapper.countByNameInParent(0L, "test.txt")).thenReturn(0);
        when(redisTemplate.expire(anyString(), any())).thenReturn(true);

        var resp = storageService.initUpload(req);

        assertNotNull(resp.getUploadId());
        assertEquals(2, resp.getTotalChunks());
        assertEquals(2, resp.getChunkUrls().size());
    }
}
