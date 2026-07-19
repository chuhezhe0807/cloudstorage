package com.chuhezhe.core.storage.dto;

import lombok.Data;

import java.util.List;

@Data
public class BatchDownloadRequest {

    private List<Long> fileIds;
}
