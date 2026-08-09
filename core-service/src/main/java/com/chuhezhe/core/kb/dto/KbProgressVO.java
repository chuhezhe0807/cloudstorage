package com.chuhezhe.core.kb.dto;

import lombok.Data;

import java.util.List;

@Data
public class KbProgressVO {

    private Long kbId;
    private Long fileId;
    private String folderName;
    private Integer totalFiles;
    private Integer processedFiles;
    private String status;
    private List<FailedFile> failedFiles;

    @Data
    public static class FailedFile {
        private String fileName;
        private String reason;
    }
}
