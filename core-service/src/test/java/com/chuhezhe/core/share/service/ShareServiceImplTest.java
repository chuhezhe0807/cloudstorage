package com.chuhezhe.core.share.service;

import com.chuhezhe.common.context.TenantContext;
import com.chuhezhe.core.file.entity.FileMeta;
import com.chuhezhe.core.file.mapper.FileMetaMapper;
import com.chuhezhe.core.share.dto.CreateShareRequest;
import com.chuhezhe.core.share.dto.ShareInfoResponse;
import com.chuhezhe.core.share.dto.ShareVO;
import com.chuhezhe.core.share.mapper.ShareLinkMapper;
import com.chuhezhe.core.storage.service.MinioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareServiceImplTest {

    @Mock
    private ShareLinkMapper shareLinkMapper;

    @Mock
    private FileMetaMapper fileMetaMapper;

    @Mock
    private MinioService minioService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private ShareServiceImpl shareService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        TenantContext.setUserId(100L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void createShareSuccess() {
        CreateShareRequest req = new CreateShareRequest();
        req.setFileId(1L);
        req.setMaxDownloads(10);

        FileMeta file = new FileMeta();
        file.setId(1L);
        file.setTenantId(1L);
        file.setName("test.txt");

        when(fileMetaMapper.selectById(1L)).thenReturn(file);

        ShareVO result = shareService.create(req);

        assertNotNull(result);
        assertNotNull(result.getCode());
        assertTrue(result.getCode().length() == 32);
        assertTrue(result.isHasPassword()); // 自动生成 6 位提取码
        verify(shareLinkMapper).insert(any());
    }

    @Test
    void createShareFileNotFoundThrows() {
        CreateShareRequest req = new CreateShareRequest();
        req.setFileId(999L);

        when(fileMetaMapper.selectById(999L)).thenReturn(null);

        assertThrows(com.chuhezhe.common.exception.BusinessException.class,
                () -> shareService.create(req));
    }

    @Test
    void getShareInfoSuccess() {
        com.chuhezhe.core.share.entity.ShareLink link =
                new com.chuhezhe.core.share.entity.ShareLink();
        link.setId(1L);
        link.setFileId(1L);
        link.setCode("abc123");
        link.setStatus("active");
        link.setDownloadCount(2);
        link.setMaxDownloads(10);

        FileMeta file = new FileMeta();
        file.setId(1L);
        file.setName("test.txt");
        file.setSize(1024L);
        file.setIsDir(false);

        when(shareLinkMapper.findByCode("abc123")).thenReturn(link);
        when(fileMetaMapper.selectById(1L)).thenReturn(file);

        ShareInfoResponse result = shareService.getShareInfo("abc123");

        assertEquals("test.txt", result.getFileName());
        assertEquals(1024L, result.getFileSize());
        assertFalse(result.isDir());
        assertEquals(10, result.getMaxDownloads());
        assertEquals(2, result.getDownloadCount());
    }

    @Test
    void getShareInfoNotFoundThrows() {
        when(shareLinkMapper.findByCode("invalid")).thenReturn(null);

        assertThrows(com.chuhezhe.common.exception.BusinessException.class,
                () -> shareService.getShareInfo("invalid"));
    }

    @Test
    void cancelShareSuccess() {
        com.chuhezhe.core.share.entity.ShareLink link =
                new com.chuhezhe.core.share.entity.ShareLink();
        link.setId(1L);
        link.setTenantId(1L);

        when(shareLinkMapper.selectById(1L)).thenReturn(link);

        shareService.cancel(1L);

        verify(shareLinkMapper).updateById(argThat(l -> "cancelled".equals(l.getStatus())));
    }
}
