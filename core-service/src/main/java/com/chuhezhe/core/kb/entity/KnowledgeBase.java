package com.chuhezhe.core.kb.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chuhezhe.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_base")
public class KnowledgeBase extends BaseEntity {

    private Long tenantId;
    private Long ownerId;
    private Long fileId;
    private String name;
    private String description;
    private String status;
    private Integer totalFiles;
    private Integer processedFiles;

    @TableField("failed_files")
    private String failedFilesJson;
}
