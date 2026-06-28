package com.chuhezhe.core.share.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建分享请求。
 */
@Data
public class CreateShareRequest {

    @Positive
    private Long fileId;

    /** 提取码，不传则自动生成 6 位 */
    private String password;

    /** 过期时间，不传则永不过期 */
    private LocalDateTime expireAt;

    /** 最大下载次数，不传则无限制 */
    private Integer maxDownloads;
}
