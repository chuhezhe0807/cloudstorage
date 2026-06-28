package com.chuhezhe.core.share.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享链接 VO。
 */
@Data
public class ShareVO {

    private Long id;
    private Long fileId;
    private String fileName;
    private String code;
    private boolean hasPassword;
    private LocalDateTime expireAt;
    private Integer maxDownloads;
    private Integer downloadCount;
    private String status;
    private LocalDateTime createdAt;
}
