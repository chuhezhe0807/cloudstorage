package com.chuhezhe.core.file.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileMetaVO {

    private Long id;
    private Long tenantId;
    private Long ownerId;
    private Long parentId;
    private String name;
    private String path;
    private Boolean isDir;
    private Long size;
    private String hash;
    private String mimeType;
    private String status;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
