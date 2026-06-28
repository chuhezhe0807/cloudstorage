package com.chuhezhe.core.share.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 分享访问成功响应。
 */
@Data
@AllArgsConstructor
public class ShareAccessResponse {

    private Long fileId;
    private String fileName;
    private long fileSize;
    private String downloadUrl;
    private Integer remainingDownloads;
}
