package com.chuhezhe.core.share.entity;

import com.chuhezhe.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 分享链接实体：code 全局唯一，支持提取码、过期时间、下载次数限制。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("share_link")
public class ShareLink extends BaseEntity {

    private Long tenantId;
    private Long ownerId;
    private Long fileId;
    private String code;
    private String passwordHash;
    private LocalDateTime expireAt;
    private Integer maxDownloads;
    private Integer downloadCount;
    private String status;
}
