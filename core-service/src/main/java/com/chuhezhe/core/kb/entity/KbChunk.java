package com.chuhezhe.core.kb.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chuhezhe.common.handler.VectorTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "kb_chunk", autoResultMap = true)
public class KbChunk {

    private Long id;
    private Long tenantId;
    private Long kbId;
    private Long fileId;
    private Integer chunkIndex;
    private String content;

    @TableField(typeHandler = VectorTypeHandler.class)
    private float[] embedding;

    private String metadata;
    private LocalDateTime createdAt;
}
