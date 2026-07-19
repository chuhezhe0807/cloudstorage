package com.chuhezhe.core.share.dto;

import lombok.Data;

import java.util.List;

@Data
public class ShareAccessResponse {

    private Long fileId;
    private String fileName;
    private long fileSize;
    private boolean isDir;
    private List<ShareFileNode> children;
    private String downloadUrl;
    private Integer remainingDownloads;
}
