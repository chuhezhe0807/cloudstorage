package com.chuhezhe.core.storage.dto;

import lombok.Data;

import java.util.List;

/**
 * 分片上传进度。
 */
@Data
public class UploadProgressResponse {

    private String uploadId;
    private int totalChunks;
    private List<Integer> uploadedChunks;
    private String status; // uploading / completed
}
