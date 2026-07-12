package com.chuhezhe.core.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DownloadUrlResponse {

    private String downloadUrl;
    private String fileName;
}
