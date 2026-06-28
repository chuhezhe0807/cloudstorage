package com.chuhezhe.core.file.entity;

import com.chuhezhe.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件物理内容实体：记录文件 hash → MinIO 对象映射 + 引用计数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_content")
public class FileContent extends BaseEntity {

    private Long tenantId;
    private String hash;
    private Long size;
    private String storageKey;
    private Integer refCount;
}
