package com.chuhezhe.core.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 分片上传初始化响应：uploadId + 各分片预签名 URL。
 */
@Data
@AllArgsConstructor
public class UploadInitResponse {

    private String uploadId;
    private int totalChunks;
    private List<ChunkUrl> chunkUrls;

    @Data
    @AllArgsConstructor
    public static class ChunkUrl {
        private int index;
        private String url;
    }
}
