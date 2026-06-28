package com.chuhezhe.core.share.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 更新分享请求。
 */
@Data
public class UpdateShareRequest {

    private String password;
    private LocalDateTime expireAt;
    private Integer maxDownloads;
}
