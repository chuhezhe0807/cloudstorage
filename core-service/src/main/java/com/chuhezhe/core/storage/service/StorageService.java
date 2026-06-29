package com.chuhezhe.core.storage.service;

import com.chuhezhe.core.storage.dto.*;

public interface StorageService {

    /** 秒传校验：hash 命中直接建引用，未命中返回 null（走分片上传） */
    FileUploadResponse checkHash(CheckHashRequest request);

    /** 初始化分片上传，返回 uploadId + 分片预签名 URL */
    UploadInitResponse initUpload(UploadInitRequest request);

    /** 查询分片上传进度 */
    UploadProgressResponse getUploadProgress(String uploadId);

    /** 确认单个分片已上传（客户端直传 MinIO 后回调） */
    void confirmChunk(String uploadId, int chunkIndex);

    /** 完成合并分片，写元数据 + outbox */
    FileUploadResponse completeUpload(String uploadId);
}
