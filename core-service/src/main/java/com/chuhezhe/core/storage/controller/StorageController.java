package com.chuhezhe.core.storage.controller;

import com.chuhezhe.common.context.TenantContext;
import com.chuhezhe.common.exception.BusinessException;
import com.chuhezhe.common.result.ErrorCode;
import com.chuhezhe.common.result.Result;
import com.chuhezhe.core.file.entity.FileMeta;
import com.chuhezhe.core.file.mapper.FileMetaMapper;
import com.chuhezhe.core.storage.dto.*;
import com.chuhezhe.core.storage.service.MinioService;
import com.chuhezhe.core.storage.service.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 存储接口：秒传、分片上传、下载。
 */
@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private static final int DOWNLOAD_TTL_SECONDS = 300; // 预签名下载 URL 5分钟

    private final StorageService storageService;
    private final MinioService minioService;
    private final FileMetaMapper fileMetaMapper;

    /** 秒传校验：命中则直接建引用返回，未命中返回 404 让前端走分片 */
    @PostMapping("/check-hash")
    public ResponseEntity<Result<FileUploadResponse>> checkHash(@Valid @RequestBody CheckHashRequest request) {
        FileUploadResponse resp = storageService.checkHash(request);
        if (resp == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(Result.ok(resp));
    }

    /** 初始化分片上传，返回 uploadId + 分片预签名 PUT URL */
    @PostMapping("/upload/init")
    public Result<UploadInitResponse> initUpload(@Valid @RequestBody UploadInitRequest request) {
        return Result.ok(storageService.initUpload(request));
    }

    /** 查询分片上传进度（断点续传） */
    @GetMapping("/upload/{uploadId}")
    public Result<UploadProgressResponse> getProgress(@PathVariable String uploadId) {
        return Result.ok(storageService.getUploadProgress(uploadId));
    }

    /** 通知合并分片 */
    @PostMapping("/upload/{uploadId}/complete")
    public Result<FileUploadResponse> completeUpload(@PathVariable String uploadId) {
        return Result.ok(storageService.completeUpload(uploadId));
    }

    /** 下载文件：302 重定向到 MinIO 预签名 GET URL */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Void> download(@PathVariable Long fileId) {
        Long tenantId = TenantContext.getTenantId();
        FileMeta fileMeta = fileMetaMapper.selectById(fileId);
        if (fileMeta == null || !tenantId.equals(fileMeta.getTenantId())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        String presignedUrl = minioService.presignedGetUrl(fileMeta.getContentRef(), DOWNLOAD_TTL_SECONDS);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, presignedUrl)
                .build();
    }
}
