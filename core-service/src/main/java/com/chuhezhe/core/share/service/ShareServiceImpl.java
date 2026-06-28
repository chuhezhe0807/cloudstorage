package com.chuhezhe.core.share.service;

import com.chuhezhe.common.context.TenantContext;
import com.chuhezhe.common.exception.BusinessException;
import com.chuhezhe.common.result.ErrorCode;
import com.chuhezhe.core.file.entity.FileMeta;
import com.chuhezhe.core.file.mapper.FileMetaMapper;
import com.chuhezhe.core.share.dto.*;
import com.chuhezhe.core.share.entity.ShareLink;
import com.chuhezhe.core.share.mapper.ShareLinkMapper;
import com.chuhezhe.core.storage.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分享服务：创建链接、访问校验、管理。
 */
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
    private final MinioService minioService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
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
    @Transactional
    public ShareAccessResponse access(String code, ShareAccessRequest request) {
        // 提取码爆破保护
        String lockKey = String.format(SHARE_LOCK_KEY, code);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw new BusinessException(ErrorCode.SHARE_LOCKED);
        }

        ShareLink link = shareLinkMapper.findByCode(code);
        if (link == null || !"active".equals(link.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_SHARE_CODE);
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
        FileMeta fileMeta = fileMetaMapper.selectById(link.getFileId());
        if (fileMeta == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        // 签发预签名下载 URL
        String downloadUrl = minioService.presignedGetUrl(fileMeta.getContentRef(), DOWNLOAD_TTL_SECONDS);

        // 递增下载次数
        link.setDownloadCount(link.getDownloadCount() + 1);
        shareLinkMapper.updateById(link);

        log.info("分享访问成功: code={}, fileId={}", code, link.getFileId());
        return new ShareAccessResponse(
                fileMeta.getId(), fileMeta.getName(), fileMeta.getSize(), downloadUrl,
                link.getMaxDownloads() != null ? link.getMaxDownloads() - link.getDownloadCount() : null);
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
}
