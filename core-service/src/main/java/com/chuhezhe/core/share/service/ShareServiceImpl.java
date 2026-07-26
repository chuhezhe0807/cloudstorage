package com.chuhezhe.core.share.service;

import com.chuhezhe.common.context.TenantContext;
import com.chuhezhe.common.exception.BusinessException;
import com.chuhezhe.common.mq.EventType;
import com.chuhezhe.common.result.ErrorCode;
import com.chuhezhe.core.file.entity.FileMeta;
import com.chuhezhe.core.file.mapper.FileMetaMapper;
import com.chuhezhe.core.share.dto.*;
import com.chuhezhe.core.share.entity.ShareLink;
import com.chuhezhe.core.share.mapper.ShareLinkMapper;
import com.chuhezhe.core.storage.entity.OutboxEvent;
import com.chuhezhe.core.storage.mapper.OutboxEventMapper;
import com.chuhezhe.core.storage.service.MinioService;
import com.chuhezhe.core.storage.service.FileZipService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private static final String SHARE_FAIL_KEY = "share_fail:%s";
    private static final String SHARE_LOCK_KEY = "share_lock:%s";
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 32;
    private static final int PASSWORD_LENGTH = 6;
    private static final int DOWNLOAD_TTL_SECONDS = 300;

    private final ShareLinkMapper shareLinkMapper;
    private final FileMetaMapper fileMetaMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;
    private final MinioService minioService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final FileZipService fileZipService;
    private final SecureRandom secureRandom = new SecureRandom();

    // ==================== 创建 ====================

    @Override
    @Transactional
    public ShareVO create(CreateShareRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        // 校验文件存在
        FileMeta fileMeta = fileMetaMapper.selectById(request.getFileId());
        if (fileMeta == null || !tenantId.equals(fileMeta.getTenantId())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        // 生成全局唯一 code 和提取码
        String code = generateRandomCode();
        String password = request.getPassword() != null ? request.getPassword() : generatePassword();
        String passwordHash = request.getPassword() != null ? passwordEncoder.encode(request.getPassword()) : null;

        ShareLink link = new ShareLink();
        link.setTenantId(tenantId);
        link.setOwnerId(userId);
        link.setFileId(request.getFileId());
        link.setCode(code);
        link.setPasswordHash(passwordHash);
        link.setExpireAt(request.getExpireAt());
        link.setMaxDownloads(request.getMaxDownloads());
        link.setDownloadCount(0);
        link.setStatus("active");
        shareLinkMapper.insert(link);

        log.info("分享创建: code={}, fileId={}", code, request.getFileId());
        return toVO(link, fileMeta, request.getPassword() != null);
    }

    // ==================== 访问 ====================

    @Override
    public ShareInfoResponse getShareInfo(String code) {
        ShareLink link = shareLinkMapper.findByCode(code);
        if (link == null || !"active".equals(link.getStatus())) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }

        if (link.getExpireAt() != null && link.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SHARE_EXPIRED);
        }

        if (link.getMaxDownloads() != null && link.getDownloadCount() >= link.getMaxDownloads()) {
            throw new BusinessException(ErrorCode.SHARE_EXHAUSTED);
        }

        FileMeta fileMeta = queryFileWithShareTenant(link);
        if (fileMeta == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        return new ShareInfoResponse(
                fileMeta.getName(),
                fileMeta.getSize() != null ? fileMeta.getSize() : 0L,
                Boolean.TRUE.equals(fileMeta.getIsDir()),
                link.getPasswordHash() != null,
                link.getExpireAt(),
                link.getMaxDownloads(),
                link.getDownloadCount());
    }

    @Override
    @Transactional
    public ShareAccessResponse access(String code, ShareAccessRequest request) {
        String lockKey = String.format(SHARE_LOCK_KEY, code);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw new BusinessException(ErrorCode.SHARE_LOCKED);
        }

        ShareLink link = shareLinkMapper.findByCode(code);
        if (link == null || !"active".equals(link.getStatus())) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }

        // 过期校验
        if (link.getExpireAt() != null && link.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SHARE_EXPIRED);
        }

        // 下载次数校验
        if (link.getMaxDownloads() != null && link.getDownloadCount() >= link.getMaxDownloads()) {
            throw new BusinessException(ErrorCode.SHARE_EXHAUSTED);
        }

        // 提取码校验
        if (link.getPasswordHash() != null) {
            if (request.getPassword() == null || !passwordEncoder.matches(request.getPassword(), link.getPasswordHash())) {
                recordShareFail(code);
                throw new BusinessException(ErrorCode.INVALID_SHARE_PASSWORD);
            }
        }

        // 清除失败计数
        clearShareFail(code);

        // 查询文件信息
        FileMeta fileMeta = queryFileWithShareTenant(link);
        if (fileMeta == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        link.setDownloadCount(link.getDownloadCount() + 1);
        shareLinkMapper.updateById(link);

        Integer remainingDownloads = link.getMaxDownloads() != null ? link.getMaxDownloads() - link.getDownloadCount() : null;

        ShareAccessResponse resp = new ShareAccessResponse();
        resp.setFileId(fileMeta.getId());
        resp.setFileName(fileMeta.getName());
        resp.setFileSize(fileMeta.getSize() != null ? fileMeta.getSize() : 0L);
        resp.setDir(Boolean.TRUE.equals(fileMeta.getIsDir()));
        resp.setRemainingDownloads(remainingDownloads);

        if (resp.isDir()) {
            List<ShareFileNode> tree = buildDirTree(link, fileMeta.getId());
            resp.setChildren(tree);
            log.info("目录分享访问成功: code={}, dirId={}", code, link.getFileId());
        } else {
            String downloadUrl = minioService.presignedGetUrl(fileMeta.getContentRef(), DOWNLOAD_TTL_SECONDS);
            resp.setDownloadUrl(downloadUrl);
            log.info("分享访问成功: code={}, fileId={}", code, link.getFileId());
        }

        publishShareEvent(link.getId(), EventType.SHARE_ACCESSED, link, fileMeta);
        return resp;
    }

    @Override
    public byte[] downloadFiles(String code, List<Long> fileIds) {
        ShareLink link = validateShareForAccess(code);

        Long originalTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(link.getTenantId());
            byte[] zipData = fileZipService.zipFiles(fileIds);
            link.setDownloadCount(link.getDownloadCount() + 1);
            shareLinkMapper.updateById(link);
            log.info("分享批量下载: code={}", code);
            return zipData;
        } finally {
            TenantContext.setTenantId(originalTenantId);
        }
    }

    // ==================== 管理 ====================

    @Override
    public List<ShareVO> listMyShares() {
        Long userId = TenantContext.getUserId();
        List<ShareLink> links = shareLinkMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShareLink>()
                        .eq(ShareLink::getOwnerId, userId)
                        .orderByDesc(ShareLink::getCreatedAt));

        return links.stream()
                .map(link -> {
                    FileMeta file = fileMetaMapper.selectById(link.getFileId());
                    return toVO(link, file, link.getPasswordHash() != null);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void cancel(Long shareId) {
        Long tenantId = TenantContext.getTenantId();
        ShareLink link = shareLinkMapper.selectById(shareId);
        if (link == null || !tenantId.equals(link.getTenantId())) {
            throw new BusinessException(ErrorCode.INVALID_SHARE_CODE);
        }
        link.setStatus("cancelled");
        shareLinkMapper.updateById(link);
        log.info("分享已取消: id={}", shareId);
    }

    @Override
    @Transactional
    public ShareVO update(Long shareId, UpdateShareRequest request) {
        Long tenantId = TenantContext.getTenantId();
        ShareLink link = shareLinkMapper.selectById(shareId);
        if (link == null || !tenantId.equals(link.getTenantId())) {
            throw new BusinessException(ErrorCode.INVALID_SHARE_CODE);
        }

        if (request.getPassword() != null) {
            link.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getExpireAt() != null) {
            link.setExpireAt(request.getExpireAt());
        }
        if (request.getMaxDownloads() != null) {
            link.setMaxDownloads(request.getMaxDownloads());
        }
        shareLinkMapper.updateById(link);

        FileMeta file = fileMetaMapper.selectById(link.getFileId());
        return toVO(link, file, link.getPasswordHash() != null);
    }

    // ==================== 私有方法 ====================

    private ShareLink validateShareForAccess(String code) {
        String lockKey = String.format(SHARE_LOCK_KEY, code);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw new BusinessException(ErrorCode.SHARE_LOCKED);
        }

        ShareLink link = shareLinkMapper.findByCode(code);
        if (link == null || !"active".equals(link.getStatus())) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }

        if (link.getExpireAt() != null && link.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SHARE_EXPIRED);
        }

        if (link.getMaxDownloads() != null && link.getDownloadCount() >= link.getMaxDownloads()) {
            throw new BusinessException(ErrorCode.SHARE_EXHAUSTED);
        }

        return link;
    }

    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(secureRandom.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        String digits = "0123456789";
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(digits.charAt(secureRandom.nextInt(digits.length())));
        }
        return sb.toString();
    }

    private void recordShareFail(String code) {
        String failKey = String.format(SHARE_FAIL_KEY, code);
        Long count = redisTemplate.opsForValue().increment(failKey);
        redisTemplate.expire(failKey, Duration.ofMinutes(10));
        if (count != null && count >= 5) {
            redisTemplate.opsForValue().set(String.format(SHARE_LOCK_KEY, code), "1", Duration.ofMinutes(30));
            log.warn("分享提取锁定: code={}", code);
        }
    }

    private void clearShareFail(String code) {
        redisTemplate.delete(String.format(SHARE_FAIL_KEY, code));
        redisTemplate.delete(String.format(SHARE_LOCK_KEY, code));
    }

    private FileMeta queryFileWithShareTenant(ShareLink link) {
        Long originalTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(link.getTenantId());
            return fileMetaMapper.selectById(link.getFileId());
        } finally {
            TenantContext.setTenantId(originalTenantId);
        }
    }

    private List<ShareFileNode> buildDirTree(ShareLink link, Long parentId) {
        Long originalTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(link.getTenantId());
            List<FileMeta> children = fileMetaMapper.listByParent(parentId);
            List<ShareFileNode> tree = new ArrayList<>();
            for (FileMeta child : children) {
                ShareFileNode node = new ShareFileNode();
                node.setId(child.getId());
                node.setName(child.getName());
                node.setDir(Boolean.TRUE.equals(child.getIsDir()));
                node.setSize(child.getSize() != null ? child.getSize() : 0L);
                if (node.isDir()) {
                    node.setChildren(buildDirTree(link, child.getId()));
                }
                tree.add(node);
            }
            tree.sort(Comparator.comparing(ShareFileNode::isDir).reversed()
                    .thenComparing(ShareFileNode::getName));
            return tree;
        } finally {
            TenantContext.setTenantId(originalTenantId);
        }
    }

    private ShareVO toVO(ShareLink link, FileMeta file, boolean hasPassword) {
        ShareVO vo = new ShareVO();
        vo.setId(link.getId());
        vo.setFileId(link.getFileId());
        vo.setFileName(file != null ? file.getName() : null);
        vo.setCode(link.getCode());
        vo.setHasPassword(hasPassword);
        vo.setExpireAt(link.getExpireAt());
        vo.setMaxDownloads(link.getMaxDownloads());
        vo.setDownloadCount(link.getDownloadCount());
        vo.setStatus(link.getStatus());
        vo.setCreatedAt(link.getCreatedAt());
        return vo;
    }

    private void publishShareEvent(Long shareId, EventType eventType, ShareLink link, FileMeta fileMeta) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("shareId", shareId);
        payload.put("code", link.getCode());
        payload.put("fileId", link.getFileId());
        payload.put("tenantId", link.getTenantId());
        payload.put("ownerId", link.getOwnerId());
        payload.put("fileName", fileMeta != null ? fileMeta.getName() : null);

        OutboxEvent event = new OutboxEvent();
        event.setAggregateId(String.valueOf(shareId));
        event.setEventType(eventType.getTypeName());
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化 outbox 事件失败", e);
        }
        event.setStatus("pending");
        event.setRetries(0);
        outboxEventMapper.insert(event);
    }
}
