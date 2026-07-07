package com.chuhezhe.core.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 秒传校验请求：客户端发 hash 判断是否已存在。
 */
@Data
public class CheckHashRequest {

    @NotBlank
    private String hash;

    @NotBlank
    private String fileName;

    @Positive
    private long fileSize;

    private long parentId;
}
