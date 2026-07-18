package com.chuhezhe.core.share.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ShareInfoResponse {

    private String fileName;
    private long fileSize;
    private boolean isDir;
    private LocalDateTime expireAt;
    private Integer maxDownloads;
    private Integer downloadCount;
}
