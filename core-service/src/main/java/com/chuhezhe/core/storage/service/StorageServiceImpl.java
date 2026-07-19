package com.chuhezhe.core.storage.service;

import com.chuhezhe.common.context.TenantContext;
import com.chuhezhe.common.exception.BusinessException;
import com.chuhezhe.common.result.ErrorCode;
import com.chuhezhe.core.file.entity.FileContent;
import com.chuhezhe.core.file.entity.FileMeta;
import com.chuhezhe.core.file.mapper.FileContentMapper;
import com.chuhezhe.core.file.mapper.FileMetaMapper;
import com.chuhezhe.core.storage.dto.*;
import com.chuhezhe.core.storage.entity.OutboxEvent;
import com.chuhezhe.core.storage.mapper.OutboxEventMapper;
import com.chuhezhe.core.storage.util.FileSecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 存储服务：秒传、分片上传、合并、下载。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private static final String UPLOAD_META_KEY = "upload:meta:%s";     // uploadId → 序列化元数据
    private static final String UPLOAD_CHUNKS_KEY = "upload:chunks:%s"; // uploadId → Set<chunkIndex>
    private static final int PRESIGNED_TTL_SECONDS = 600;               // 预签名 URL 10分钟

    private final MinioService minioService;
    private final FileMetaMapper fileMetaMapper;
    private final FileContentMapper fileContentMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    // ==================== 秒传 ====================

    @Override
    @Transactional
    public FileUploadResponse checkHash(CheckHashRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        // 文件名安全校验
        FileSecurityUtil.validateFileName(request.getFileName());
        FileSecurityUtil.validateExtension(request.getFileName());
        FileSecurityUtil.validateFileSize(request.getFileSize());

        // 查找同租户下相同 hash 的内容
        FileContent existing = fileContentMapper.findByHash(request.getHash());
        if (existing == null) {
            return null; // 未命中，需要走分片上传
        }

        // 秒传命中：建新 file_meta 引用，引用计数+1
        long parentId = request.getParentId() > 0 ? request.getParentId() : 0L;
        FileMeta meta = new FileMeta();
        meta.setTenantId(tenantId);
        meta.setOwnerId(userId);
        meta.setParentId(parentId);
        meta.setName(request.getFileName());
        meta.setPath(buildFilePath(parentId, request.getFileName()));
        meta.setIsDir(false);
        meta.setSize(request.getFileSize());
        meta.setHash(request.getHash());
        meta.setContentRef(existing.getStorageKey());
        meta.setStatus("active");
        fileMetaMapper.insert(meta);

        existing.setRefCount(existing.getRefCount() + 1);
        fileContentMapper.updateById(existing);

        log.info("秒传成功: hash={}, fileId={}", request.getHash(), meta.getId());
        publishEvent(meta.getId(), "upload.completed", meta);
        return new FileUploadResponse(meta.getId(), request.getFileName(), request.getFileSize(), true);
    }

    // ==================== 分片上传 ====================

    @Override
    public UploadInitResponse initUpload(UploadInitRequest request) {
        Long tenantId = TenantContext.getTenantId();
        String fileName = request.getFileName();
        long parentId = request.getParentId() > 0 ? request.getParentId() : 0L;

        // 安全校验
        FileSecurityUtil.validateFileName(fileName);
        FileSecurityUtil.validateExtension(fileName);
        FileSecurityUtil.validateFileSize(request.getTotalSize());
        if (request.getChunkSize() != FileSecurityUtil.CHUNK_SIZE) {
            throw new IllegalArgumentException("分片大小只能为 " + (FileSecurityUtil.CHUNK_SIZE / 1024 / 1024) + "MB");
        }

        // 校验文件名唯一性
        if (fileMetaMapper.countByNameInParent(parentId, fileName) > 0) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT);
        }

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        int totalChunks = (int) Math.ceil((double) request.getTotalSize() / request.getChunkSize());
        String tenantPrefix = minioService.getTenantPrefix();

        // 生成分片预签名 URL
        List<UploadInitResponse.ChunkUrl> chunkUrls = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            String chunkKey = tenantPrefix + uploadId + "/chunk_" + i;
            String url = minioService.presignedPutUrl(chunkKey, PRESIGNED_TTL_SECONDS);
            chunkUrls.add(new UploadInitResponse.ChunkUrl(i, url));
        }

        // 存储上传元数据到 Redis
        Map<String, String> metaMap = new HashMap<>();
        metaMap.put("fileName", fileName);
        metaMap.put("totalSize", String.valueOf(request.getTotalSize()));
        metaMap.put("totalChunks", String.valueOf(totalChunks));
        metaMap.put("hash", request.getHash() != null ? request.getHash() : "");
        metaMap.put("tenantPrefix", tenantPrefix);
        metaMap.put("parentId", String.valueOf(parentId));
        redisTemplate.opsForHash().putAll(String.format(UPLOAD_META_KEY, uploadId), metaMap);
        redisTemplate.expire(String.format(UPLOAD_META_KEY, uploadId), Duration.ofHours(24));

        log.info("分片上传初始化: uploadId={}, totalChunks={}", uploadId, totalChunks);
        return new UploadInitResponse(uploadId, totalChunks, chunkUrls);
    }

    @Override
    public UploadProgressResponse getUploadProgress(String uploadId) {
        String metaKey = String.format(UPLOAD_META_KEY, uploadId);
        String chunksKey = String.format(UPLOAD_CHUNKS_KEY, uploadId);

        Map<Object, Object> meta = redisTemplate.opsForHash().entries(metaKey);
        if (meta.isEmpty()) {
            throw new BusinessException(ErrorCode.UPLOAD_NOT_FOUND);
        }

        int totalChunks = Integer.parseInt((String) meta.get("totalChunks"));
        Set<String> uploadedSet = redisTemplate.opsForSet().members(chunksKey);
        List<Integer> uploadedIdx = uploadedSet != null
                ? uploadedSet.stream().map(Integer::parseInt).sorted().collect(Collectors.toList())
                : List.of();

        UploadProgressResponse resp = new UploadProgressResponse();
        resp.setUploadId(uploadId);
        resp.setTotalChunks(totalChunks);
        resp.setUploadedChunks(uploadedIdx);
        resp.setStatus(uploadedIdx.size() >= totalChunks ? "completed" : "uploading");
        return resp;
    }

    @Override
    public void confirmChunk(String uploadId, int chunkIndex) {
        String metaKey = String.format(UPLOAD_META_KEY, uploadId);
        if (redisTemplate.opsForHash().entries(metaKey).isEmpty()) {
            throw new BusinessException(ErrorCode.UPLOAD_NOT_FOUND);
        }
        String chunksKey = String.format(UPLOAD_CHUNKS_KEY, uploadId);
        redisTemplate.opsForSet().add(chunksKey, String.valueOf(chunkIndex));
        redisTemplate.expire(chunksKey, Duration.ofHours(24));
        log.info("分片确认: uploadId={}, chunkIndex={}", uploadId, chunkIndex);
    }

    @Override
    @Transactional
    public FileUploadResponse completeUpload(String uploadId) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        String metaKey = String.format(UPLOAD_META_KEY, uploadId);
        String chunksKey = String.format(UPLOAD_CHUNKS_KEY, uploadId);

        Map<Object, Object> meta = redisTemplate.opsForHash().entries(metaKey);
        if (meta.isEmpty()) {
            throw new BusinessException(ErrorCode.UPLOAD_NOT_FOUND);
        }

        String fileName = (String) meta.get("fileName");
        long totalSize = Long.parseLong((String) meta.get("totalSize"));
        int totalChunks = Integer.parseInt((String) meta.get("totalChunks"));
        String hash = (String) meta.get("hash");
        String tenantPrefix = (String) meta.get("tenantPrefix");
        long parentId = Long.parseLong((String) meta.getOrDefault("parentId", "0"));

        // 校验全部分片已上传
        Set<String> uploadedSet = redisTemplate.opsForSet().members(chunksKey);
        if (uploadedSet == null || uploadedSet.size() < totalChunks) {
            List<Integer> missingIdx = IntStream.range(0, totalChunks)
                    .filter(i -> !uploadedSet.contains(String.valueOf(i)))
                    .boxed()
                    .collect(Collectors.toList());
            throw new BusinessException(ErrorCode.CHUNK_MISSING);
        }

        // MinIO compose 合并对象
        String targetKey = tenantPrefix + uploadId + "/" + fileName;
        List<String> chunkKeys = IntStream.range(0, totalChunks)
                .mapToObj(i -> tenantPrefix + uploadId + "/chunk_" + i)
                .collect(Collectors.toList());
        minioService.composeObject(targetKey, chunkKeys);

        // 写 file_content（去重：同租户同 hash 只存一份）
        FileContent fileContent = fileContentMapper.findByHash(hash);
        if (fileContent != null) {
            fileContent.setRefCount(fileContent.getRefCount() + 1);
            fileContentMapper.updateById(fileContent);
        } else {
            fileContent = new FileContent();
            fileContent.setTenantId(tenantId);
            fileContent.setHash(hash);
            fileContent.setSize(totalSize);
            fileContent.setStorageKey(targetKey);
            fileContent.setRefCount(1);
            fileContentMapper.insert(fileContent);
        }

        // 写 file_meta
        FileMeta metaEntity = new FileMeta();
        metaEntity.setTenantId(tenantId);
        metaEntity.setOwnerId(userId);
        metaEntity.setParentId(parentId);
        metaEntity.setName(fileName);
        metaEntity.setPath(buildFilePath(parentId, fileName));
        metaEntity.setIsDir(false);
        metaEntity.setSize(totalSize);
        metaEntity.setHash(hash);
        metaEntity.setContentRef(targetKey);
        metaEntity.setStatus("active");
        fileMetaMapper.insert(metaEntity);

        // 清理 Redis 数据
        redisTemplate.delete(metaKey);
        redisTemplate.delete(chunksKey);

        // 清理分片对象（可选，保留则浪费空间）
        minioService.deleteChunks(chunkKeys);

        log.info("上传完成: uploadId={}, fileId={}, size={}", uploadId, metaEntity.getId(), totalSize);
        publishEvent(metaEntity.getId(), "upload.completed", metaEntity);
        return new FileUploadResponse(metaEntity.getId(), fileName, totalSize, false);
    }

    // ==================== 私有方法 ====================

    /** 将事件写入 outbox_event 表，后续由定时任务或 MQ 消费者处理 */
    /** 根据父目录构建文件的完整物化路径（文件 path 不以 / 结尾，目录才以 / 结尾） */
    private String buildFilePath(long parentId, String name) {
        if (parentId <= 0) {
            return "/" + name;
        }
        FileMeta parent = fileMetaMapper.selectById(parentId);
        if (parent == null) {
            return "/" + name;
        }
        String parentPath = parent.getPath();
        if (!parentPath.endsWith("/")) {
            parentPath = parentPath + "/";
        }
        return parentPath + name;
    }

    private void publishEvent(Long fileId, String eventType, FileMeta meta) {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setAggregateId(String.valueOf(fileId));
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(meta));
            event.setStatus("pending");
            event.setRetries(0);
            outboxEventMapper.insert(event);
        } catch (Exception e) {
            log.error("写 outbox 事件失败: fileId={}, eventType={}", fileId, eventType, e);
        }
    }
}
