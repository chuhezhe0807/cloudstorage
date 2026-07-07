package com.chuhezhe.core.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 分片上传初始化请求。
 */
@Data
public class UploadInitRequest {

    @NotBlank
    private String fileName;

    @Positive
    private long totalSize;

    /** 文件 SHA-256（秒传已校验通过则传） */
    private String hash;

    /** 分片大小，默认 5MB */
    private long chunkSize = 5 * 1024 * 1024;

    private long parentId;
}
