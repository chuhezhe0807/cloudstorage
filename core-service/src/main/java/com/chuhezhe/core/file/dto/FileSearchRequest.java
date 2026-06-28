package com.chuhezhe.core.file.dto;

import lombok.Data;

@Data
public class FileSearchRequest {

    private String keyword;
    private String type;   // file / dir
    private Long minSize;
    private Long maxSize;
    private Integer page = 1;
    private Integer size = 20;
}
