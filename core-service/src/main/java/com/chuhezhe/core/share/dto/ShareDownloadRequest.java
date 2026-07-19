package com.chuhezhe.core.share.dto;

import lombok.Data;

import java.util.List;

@Data
public class ShareDownloadRequest {

    private List<Long> fileIds;
}
