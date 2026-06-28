package com.chuhezhe.core;

import com.chuhezhe.core.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务：每天凌晨 2 点清理超过 30 天的回收站项。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupScheduler {

    private final FileService fileService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanRecycleBin() {
        log.info("开始定时清理回收站");
        int cleaned = fileService.cleanExpiredRecycled(100);
        log.info("回收站清理完成，共 {} 项", cleaned);
    }
}
