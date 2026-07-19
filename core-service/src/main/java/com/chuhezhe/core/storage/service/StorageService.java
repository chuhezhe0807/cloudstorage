package com.chuhezhe.core.storage.service;

import com.chuhezhe.core.storage.dto.*;

import java.util.List;

public interface StorageService {

    FileUploadResponse checkHash(CheckHashRequest request);

    UploadInitResponse initUpload(UploadInitRequest request);

    UploadProgressResponse getUploadProgress(String uploadId);

    void confirmChunk(String uploadId, int chunkIndex);

    FileUploadResponse completeUpload(String uploadId);

    byte[] downloadFiles(List<Long> fileIds);
}
