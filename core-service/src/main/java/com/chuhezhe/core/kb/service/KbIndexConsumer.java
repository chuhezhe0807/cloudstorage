package com.chuhezhe.core.kb.service;

import com.chuhezhe.common.context.TenantContext;
import com.chuhezhe.common.mq.RabbitMqConstants;
import com.chuhezhe.common.spi.EmbeddingProvider;
import com.chuhezhe.common.spi.LangfuseTraceHelper;
import com.chuhezhe.core.file.entity.FileContent;
import com.chuhezhe.core.file.entity.FileMeta;
import com.chuhezhe.core.file.mapper.FileContentMapper;
import com.chuhezhe.core.file.mapper.FileMetaMapper;
import com.chuhezhe.core.kb.entity.KbChunk;
import com.chuhezhe.core.kb.entity.KnowledgeBase;
import com.chuhezhe.core.kb.mapper.KbChunkMapper;
import com.chuhezhe.core.kb.mapper.KnowledgeBaseMapper;
import com.chuhezhe.core.storage.service.MinioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class KbIndexConsumer {

    private static final int LOCK_TTL_SECONDS = 3600;
    private static final int DEFAULT_CHUNK_TOKENS = 512;
    private static final int LARGE_FILE_CHUNK_TOKENS = 1024;
    private static final long LARGE_FILE_THRESHOLD = 20 * 1024 * 1024;
    private static final int CHARS_PER_TOKEN = 4;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KbChunkMapper kbChunkMapper;
    private final FileMetaMapper fileMetaMapper;
    private final FileContentMapper fileContentMapper;
    private final MinioService minioService;
    private final EmbeddingProvider embeddingProvider;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LangfuseTraceHelper langfuseTraceHelper;

    @RabbitListener(queues = RabbitMqConstants.KB_INDEX_QUEUE)
    public void onMessage(Map<String, Object> message) {
        Object kbIdObj = message.get("kbId");
        if (kbIdObj == null) {
            log.warn("Invalid kb.index.request: missing kbId");
            return;
        }
        Long kbId = Long.valueOf(kbIdObj.toString());
        Long tenantId = Long.valueOf(message.get("tenantId").toString());

        String lockKey = "kb:lock:" + kbId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));
        if (Boolean.FALSE.equals(acquired)) {
            log.debug("KB lock already held: kbId={}", kbId);
            return;
        }

        try {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
            if (kb == null || "ready".equals(kb.getStatus())) {
                log.info("KB already ready or not found: kbId={}", kbId);
                return;
            }

            TenantContext.setTenantId(tenantId);

            LangfuseTraceHelper.LangfuseTrace trace = langfuseTraceHelper.startTrace("kb-index-" + kbId, kb.getName());

            try {
                processKb(kb, trace);
            } finally {
                trace.close();
                TenantContext.clear();
            }
        } catch (Exception e) {
            log.error("KB index consumer failed: kbId={}", kbId, e);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private void processKb(KnowledgeBase kb, LangfuseTraceHelper.LangfuseTrace trace) {
        Long fileId = kb.getFileId();
        List<FileMeta> allFiles = collectAllFiles(fileId, kb.getTenantId());
        kb.setTotalFiles(allFiles.size());
        kb.setProcessedFiles(0);
        knowledgeBaseMapper.updateById(kb);

        List<Map<String, String>> failedFiles = new ArrayList<>();
        int processed = 0;

        for (FileMeta file : allFiles) {
            try {
                vectorizeFile(file, kb.getId(), kb.getTenantId());
                processed++;
                kb.setProcessedFiles(processed);
                knowledgeBaseMapper.updateById(kb);
                trace.span("vectorize-file-" + file.getId(), Map.of("fileName", file.getName(), "size", file.getSize()));
            } catch (Exception e) {
                log.warn("File vectorization failed: fileId={}, name={}", file.getId(), file.getName(), e);
                Map<String, String> failed = new HashMap<>();
                failed.put("fileName", file.getName());
                failed.put("reason", e.getMessage() != null ? e.getMessage() : "VECTORIZE_FAILED");
                failedFiles.add(failed);
            }
        }

        try {
            if (processed == 0 && !allFiles.isEmpty()) {
                kb.setStatus("failed");
            } else if (failedFiles.isEmpty()) {
                kb.setStatus("ready");
            } else {
                kb.setStatus("partial");
            }
            kb.setFailedFilesJson(objectMapper.writeValueAsString(failedFiles));
            kb.setProcessedFiles(processed);
            kb.setUpdatedAt(LocalDateTime.now());
            knowledgeBaseMapper.updateById(kb);
            log.info("KB index complete: kbId={}, status={}, processed={}/{}", kb.getId(), kb.getStatus(), processed, allFiles.size());
            trace.update("complete", Map.of("status", kb.getStatus(), "processed", processed, "total", allFiles.size()));
        } catch (Exception e) {
            log.error("Failed to finalize KB status: kbId={}", kb.getId(), e);
            kb.setStatus("failed");
            knowledgeBaseMapper.updateById(kb);
        }
    }

    private void vectorizeFile(FileMeta file, Long kbId, Long tenantId) throws Exception {
        log.info("Vectorizing file: id={}, name={}, size={}", file.getId(), file.getName(), file.getSize());

        String content = readFileContent(file);
        if (content == null || content.isEmpty()) {
            throw new RuntimeException("Empty file content");
        }

        long fileSize = file.getSize() != null ? file.getSize() : 0;
        int chunkTokens = fileSize > LARGE_FILE_THRESHOLD ? LARGE_FILE_CHUNK_TOKENS : DEFAULT_CHUNK_TOKENS;
        List<String> chunks = splitText(content, chunkTokens);

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            float[] embedding;
            try {
                embedding = embeddingProvider.embed(chunk);
            } catch (Exception e) {
                log.warn("Embedding failed for chunk {}/{} of file {}, skipping", i, chunks.size(), file.getName());
                continue;
            }

            KbChunk kbChunk = new KbChunk();
            kbChunk.setTenantId(tenantId);
            kbChunk.setKbId(kbId);
            kbChunk.setFileId(file.getId());
            kbChunk.setChunkIndex(i);
            kbChunk.setContent(chunk);
            kbChunk.setEmbedding(embedding);
            kbChunk.setMetadata("{\"chunkTokens\":" + chunkTokens + ",\"strategy\":\"fixed\"}");
            kbChunk.setCreatedAt(LocalDateTime.now());
            kbChunkMapper.insert(kbChunk);
        }

        log.info("Vectorized {} chunks for file: {}", chunks.size(), file.getName());
    }

    private String readFileContent(FileMeta file) {
        if (!isSupportedTextFile(file.getName())) {
            throw new RuntimeException("Unsupported file type for vectorization");
        }
        try {
            FileContent fc = fileContentMapper.findByHash(file.getHash());
            if (fc == null || fc.getStorageKey() == null) {
                throw new RuntimeException("No storage key for file");
            }
            InputStream is = minioService.getObjectStream(fc.getStorageKey());
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read file: " + e.getMessage());
        }
    }

    private boolean isSupportedTextFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")
                || lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".html")
                || lower.endsWith(".log") || lower.endsWith(".yaml") || lower.endsWith(".yml")
                || lower.endsWith(".properties") || lower.endsWith(".java") || lower.endsWith(".py")
                || lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".tsx")
                || lower.endsWith(".sql") || lower.endsWith(".kt") || lower.endsWith(".go")
                || lower.endsWith(".rs") || lower.endsWith(".c") || lower.endsWith(".h")
                || lower.endsWith(".cpp") || lower.endsWith(".css") || lower.endsWith(".scss")
                || lower.endsWith(".less") || lower.endsWith(".toml") || lower.endsWith(".ini")
                || lower.endsWith(".cfg") || lower.endsWith(".conf");
    }

    private List<String> splitText(String text, int chunkTokens) {
        int chunkSize = chunkTokens * CHARS_PER_TOKEN;
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < text.length(); start += chunkSize) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
        }
        return chunks;
    }

    private List<FileMeta> collectAllFiles(Long folderId, Long tenantId) {
        List<FileMeta> allFiles = new ArrayList<>();
        List<FileMeta> children = fileMetaMapper.listByParent(folderId);
        for (FileMeta child : children) {
            if (Boolean.TRUE.equals(child.getIsDir())) {
                allFiles.addAll(collectAllFiles(child.getId(), tenantId));
            } else if (!"recycled".equals(child.getStatus())) {
                allFiles.add(child);
            }
        }
        return allFiles;
    }
}
