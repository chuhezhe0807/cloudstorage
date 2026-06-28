package com.chuhezhe.core.storage.entity;

import com.chuhezhe.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件分片实体：用于持久化分片上传进度（Redis 为主，PG 兜底）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_chunk")
public class FileChunk extends BaseEntity {

    private Long tenantId;
    private String uploadId;
    private Integer chunkIndex;
    private String chunkHash;
    private String status;
}
