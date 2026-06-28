package com.chuhezhe.core.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 秒传命中或上传完成后的响应。
 */
@Data
@AllArgsConstructor
public class FileUploadResponse {

    private Long fileId;
    private String name;
    private long size;
    private boolean instantTransfer; // 是否秒传
}
