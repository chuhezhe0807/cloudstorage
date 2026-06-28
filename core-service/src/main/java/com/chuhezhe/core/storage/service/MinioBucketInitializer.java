package com.chuhezhe.core.storage.service;

import com.chuhezhe.core.storage.config.MinioConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化 MinIO Bucket。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioBucketInitializer {

    private final MinioService minioService;

    @PostConstruct
    public void init() {
        minioService.ensureBucket();
    }
}
