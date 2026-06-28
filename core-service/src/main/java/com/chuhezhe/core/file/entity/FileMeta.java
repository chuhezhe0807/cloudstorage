package com.chuhezhe.core.file.entity;

import com.chuhezhe.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件/目录元数据实体。
 * 同时维护 parent_id 和物化路径 path 双字段，支持快速子树查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_meta")
public class FileMeta extends BaseEntity {

    private Long tenantId;
    private Long ownerId;
    private Long parentId;
    private String name;
    private String path;
    private Boolean isDir;
    private Long size;
    private String hash;
    private String contentRef;
    private String mimeType;
    private String status;
    private java.time.LocalDateTime deletedAt;
}
