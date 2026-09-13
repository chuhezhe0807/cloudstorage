package com.chuhezhe.core.storage.service;

import com.chuhezhe.common.context.TenantContext;
import com.chuhezhe.core.storage.config.MinioConfig;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MinIO 操作封装：Bucket 管理、预签名 URL、对象组合。
 * 租户隔离通过 key 前缀 tenant/{tenantId}/ 实现。
 */
@Slf4j
@Service
public class MinioService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    /** 使用对外地址构建的客户端，仅用于生成浏览器可达的预签名 URL */
    private final MinioClient presignClient;

    public MinioService(MinioClient minioClient, MinioConfig minioConfig) {
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
        String publicEndpoint = minioConfig.getPublicEndpoint();
        if (publicEndpoint != null && !publicEndpoint.isBlank()
                && !publicEndpoint.equals(minioConfig.getEndpoint())) {
            this.presignClient = MinioClient.builder()
                    .endpoint(publicEndpoint)
                    .credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey())
                    .region(minioConfig.getRegion())
                    .build();
        } else {
            this.presignClient = minioClient;
        }
    }

    /** 创建 Bucket（幂等） */
    public void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
                log.info("MinIO Bucket 已创建: {}", minioConfig.getBucket());
            }
        } catch (Exception e) {
            log.error("MinIO Bucket 创建失败", e);
        }
    }

    /** 获取租户前缀：tenant/{tenantId}/ */
    public String getTenantPrefix() {
        return "tenant/" + TenantContext.getTenantId() + "/";
    }

    /** 为分片生成预签名 PUT URL */
    public String presignedPutUrl(String objectKey, int ttlSeconds) {
        try {
            return presignClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioConfig.getBucket())
                            .object(objectKey)
                            .expiry(ttlSeconds, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("生成预签名上传 URL 失败", e);
        }
    }

    /** 为对象生成预签名 GET URL（下载） */
    public String presignedGetUrl(String objectKey, int ttlSeconds) {
        try {
            return presignClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucket())
                            .object(objectKey)
                            .expiry(ttlSeconds, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("生成预签名下载 URL 失败", e);
        }
    }

    /** 合并分片对象 */
    public void composeObject(String targetKey, List<String> chunkKeys) {
        try {
            List<ComposeSource> sources = chunkKeys.stream()
                    .map(key -> ComposeSource.builder()
                            .bucket(minioConfig.getBucket())
                            .object(key)
                            .build())
                    .collect(Collectors.toList());

            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(targetKey)
                            .sources(sources)
                            .build());

            log.info("对象合并完成: {} 分片 -> {}", chunkKeys.size(), targetKey);
        } catch (Exception e) {
            throw new RuntimeException("对象合并失败", e);
        }
    }

    /** 删除对象 */
    public void deleteObject(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO 对象删除失败: {}", objectKey, e);
        }
    }

    /** 批量删除分片对象 */
    public void deleteChunks(List<String> chunkKeys) {
        for (String key : chunkKeys) {
            deleteObject(key);
        }
    }

    /** 检查对象是否存在 */
    public boolean objectExists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectKey)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 获取对象输入流 */
    public InputStream getObjectStream(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("获取对象流失败: " + objectKey, e);
        }
    }
}
