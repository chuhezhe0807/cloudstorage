package com.chuhezhe.core.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chuhezhe.common.context.TenantContext;
import com.chuhezhe.common.dto.PageResult;
import com.chuhezhe.common.exception.BusinessException;
import com.chuhezhe.common.result.ErrorCode;
import com.chuhezhe.core.file.dto.*;
import com.chuhezhe.core.file.entity.FileMeta;
import com.chuhezhe.core.file.mapper.FileMetaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件服务：目录树 CRUD、搜索、回收站。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final String ROOT_PATH = "/";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_RECYCLED = "recycled";

    /** 文件列表 Redis 缓存 TTL（分钟） */
    private static final long LIST_CACHE_TTL_MINUTES = 5;

    private final FileMetaMapper fileMetaMapper;
    private final StringRedisTemplate redisTemplate;

    // ==================== 目录操作 ====================

    @Override
    @Transactional
    public FileMetaVO createDirectory(CreateDirRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        // 确认父目录存在（0 表示根目录）
        if (request.getParentId() != null && request.getParentId() != 0) {
            FileMeta parent = fileMetaMapper.selectById(request.getParentId());
            if (parent == null || !parent.getIsDir()) {
                throw new BusinessException(ErrorCode.NOT_A_DIRECTORY);
            }
            ensureOwnership(parent, tenantId);
        }

        // 同名冲突检查
        Long parentId = request.getParentId() != null ? request.getParentId() : 0L;
        if (fileMetaMapper.countByNameInParent(parentId, request.getName()) > 0) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT);
        }

        // 构建物化路径
        String path = buildPath(parentId, request.getName());

        FileMeta dir = new FileMeta();
        dir.setTenantId(tenantId);
        dir.setOwnerId(userId);
        dir.setParentId(parentId);
        dir.setName(request.getName());
        dir.setPath(path);
        dir.setIsDir(true);
        dir.setSize(0L);
        dir.setStatus(STATUS_ACTIVE);
        fileMetaMapper.insert(dir);

        // 清除父目录缓存
        clearListCache(parentId);

        log.info("目录创建成功: path={}", path);
        return toVO(dir);
    }

    @Override
    public List<FileMetaVO> listFiles(Long parentId) {
        Long tenantId = TenantContext.getTenantId();
        parentId = parentId != null ? parentId : 0L;

        List<FileMeta> children = fileMetaMapper.listByParent(parentId);
        return children.stream().map(this::toVO).collect(Collectors.toList());
    }

    // ==================== 重命名 ====================

    @Override
    @Transactional
    public FileMetaVO rename(Long fileId, RenameRequest request) {
        FileMeta file = getFileWithCheck(fileId, STATUS_ACTIVE);

        // 同目录同名检查
        if (fileMetaMapper.countByNameInParent(file.getParentId(), request.getNewName()) > 0) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT);
        }

        String oldPath = file.getPath();
        String newPath = computeRenamedPath(oldPath, file.getName(), request.getNewName());

        updatePathAndName(file, request.getNewName(), newPath, file.getIsDir());

        log.info("重命名: {} -> {}, path: {} -> {}", file.getName(), request.getNewName(), oldPath, newPath);
        return toVO(fileMetaMapper.selectById(fileId));
    }

    // ==================== 移动 ====================

    @Override
    @Transactional
    public FileMetaVO move(Long fileId, MoveRequest request) {
        Long tenantId = TenantContext.getTenantId();
        FileMeta file = getFileWithCheck(fileId, STATUS_ACTIVE);

        FileMeta targetParent = null;
        Long targetParentId = request.getTargetParentId() != null ? request.getTargetParentId() : 0L;
        if (targetParentId != 0) {
            targetParent = fileMetaMapper.selectById(targetParentId);
            if (targetParent == null || !targetParent.getIsDir()) {
                throw new BusinessException(ErrorCode.NOT_A_DIRECTORY);
            }
            ensureOwnership(targetParent, tenantId);

            // 防止移到自己的子目录
            if (targetParent.getPath().startsWith(file.getPath())) {
                throw new BusinessException(ErrorCode.CONCURRENT_MOVE);
            }
        }

        if (fileMetaMapper.countByNameInParent(targetParentId, file.getName()) > 0) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT);
        }

        String newPath = computeMovedPath(file.getPath(), file.getName(),
                targetParent != null ? targetParent.getPath() : ROOT_PATH);

        updatePathAndName(file, file.getName(), newPath, file.getIsDir());
        fileMetaMapper.updateById(file);
        clearListCache(file.getParentId());
        clearListCache(targetParentId);

        log.info("移动成功: id={}, {} -> {}", fileId, file.getParentId(), targetParentId);
        return toVO(fileMetaMapper.selectById(fileId));
    }

    // ==================== 删除 ====================

    @Override
    @Transactional
    public void softDelete(Long fileId) {
        FileMeta file = getFileWithCheck(fileId, STATUS_ACTIVE);

        file.setStatus(STATUS_RECYCLED);
        file.setDeletedAt(LocalDateTime.now());
        fileMetaMapper.updateById(file);

        // 如果是目录，递归软删除子节点
        if (file.getIsDir()) {
            String prefix = ensureTrailingSlash(file.getPath());
            List<FileMeta> children = fileMetaMapper.listByPathPrefix(prefix);
            for (FileMeta child : children) {
                child.setStatus(STATUS_RECYCLED);
                child.setDeletedAt(LocalDateTime.now());
                fileMetaMapper.updateById(child);
            }
        }

        clearListCache(file.getParentId());
        log.info("软删除: id={}, name={}", fileId, file.getName());
    }

    @Override
    public PageResult<FileMetaVO> listRecycled(int page, int size) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<FileMeta> all = fileMetaMapper.listRecycled(cutoff);

        int total = all.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        List<FileMeta> pageData = fromIndex >= total ? List.of() : all.subList(fromIndex, toIndex);

        return new PageResult<>(
                pageData.stream().map(this::toVO).collect(Collectors.toList()),
                total, page, size);
    }

    @Override
    @Transactional
    public void restore(Long fileId) {
        FileMeta file = getFileWithCheck(fileId, STATUS_RECYCLED);

        // 检查原父目录是否仍存在
        if (file.getParentId() != 0) {
            FileMeta parent = fileMetaMapper.selectById(file.getParentId());
            if (parent == null || !STATUS_ACTIVE.equals(parent.getStatus())) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
        }

        file.setStatus(STATUS_ACTIVE);
        file.setDeletedAt(null);
        fileMetaMapper.updateById(file);

        // 如果是目录，递归还原子节点
        if (file.getIsDir()) {
            String prefix = ensureTrailingSlash(file.getPath());
            List<FileMeta> children = fileMetaMapper.listByPathPrefix(prefix);
            for (FileMeta child : children) {
                child.setStatus(STATUS_ACTIVE);
                child.setDeletedAt(null);
                fileMetaMapper.updateById(child);
            }
        }

        clearListCache(file.getParentId());
        log.info("回收站还原: id={}, name={}", fileId, file.getName());
    }

    @Override
    @Transactional
    public void permanentDelete(Long fileId) {
        FileMeta file = getFileWithCheck(fileId, STATUS_RECYCLED);

        // 如果是目录，递归永久删除子节点
        if (file.getIsDir()) {
            String prefix = ensureTrailingSlash(file.getPath());
            List<FileMeta> children = fileMetaMapper.listByPathPrefix(prefix);
            for (FileMeta child : children) {
                fileMetaMapper.deleteById(child.getId());
            }
        }

        fileMetaMapper.deleteById(file.getId());
        log.info("永久删除: id={}, name={}", fileId, file.getName());
    }

    // ==================== 搜索 ====================

    @Override
    public PageResult<FileMetaVO> search(FileSearchRequest request) {
        LambdaQueryWrapper<FileMeta> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileMeta::getStatus, STATUS_ACTIVE);

        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            wrapper.like(FileMeta::getName, request.getKeyword());
        }
        if (request.getType() != null) {
            wrapper.eq(FileMeta::getIsDir, "dir".equals(request.getType()));
        }
        if (request.getMinSize() != null) {
            wrapper.ge(FileMeta::getSize, request.getMinSize());
        }
        if (request.getMaxSize() != null) {
            wrapper.le(FileMeta::getSize, request.getMaxSize());
        }
        wrapper.orderByDesc(FileMeta::getIsDir).orderByAsc(FileMeta::getName);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileMeta> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        request.getPage(), request.getSize());
        page = fileMetaMapper.selectPage(page, wrapper);

        return new PageResult<>(
                page.getRecords().stream().map(this::toVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    // ==================== 定时清理 ====================

    @Override
    public int cleanExpiredRecycled(int batchSize) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<FileMeta> expired = fileMetaMapper.listExpiredRecycled(cutoff, batchSize);

        int count = 0;
        for (FileMeta file : expired) {
            permanentDelete(file.getId());
            count++;
        }
        if (count > 0) {
            log.info("定时清理回收站: {} 项", count);
        }
        return count;
    }

    // ==================== 私有方法 ====================

    /** 校验文件存在且属于当前租户 */
    private FileMeta getFileWithCheck(Long fileId, String expectedStatus) {
        FileMeta file = fileMetaMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        ensureOwnership(file, TenantContext.getTenantId());
        if (expectedStatus != null && !expectedStatus.equals(file.getStatus())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return file;
    }

    /** 确保文件属于指定租户 */
    private void ensureOwnership(FileMeta file, Long tenantId) {
        if (!tenantId.equals(file.getTenantId())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
    }

    /** 构建物化路径 */
    private String buildPath(Long parentId, String name) {
        if (parentId == null || parentId == 0) {
            return ROOT_PATH + name + "/";
        }
        FileMeta parent = fileMetaMapper.selectById(parentId);
        return (parent != null ? parent.getPath() : ROOT_PATH) + name + "/";
    }

    /** 计算重命名后的路径 */
    private String computeRenamedPath(String oldPath, String oldName, String newName) {
        String prefix = oldPath.substring(0, oldPath.lastIndexOf(oldName));
        return prefix + newName + (oldPath.endsWith("/") ? "/" : "");
    }

    /** 计算移动后的路径 */
    private String computeMovedPath(String oldPath, String name, String targetParentPath) {
        String basePath = targetParentPath.equals(ROOT_PATH) ? ROOT_PATH : targetParentPath;
        return basePath + name + (oldPath.endsWith("/") ? "/" : "");
    }

    /** 更新节点及子树的路径 */
    private void updatePathAndName(FileMeta file, String newName, String newPath, boolean isDir) {
        String oldPath = file.getPath();
        file.setName(newName);
        file.setPath(newPath);
        fileMetaMapper.updateById(file);

        if (isDir) {
            String oldPrefix = ensureTrailingSlash(oldPath);
            List<FileMeta> children = fileMetaMapper.listByPathPrefix(oldPrefix);
            for (FileMeta child : children) {
                child.setPath(child.getPath().replace(oldPrefix, ensureTrailingSlash(newPath)));
                fileMetaMapper.updateById(child);
            }
        }
    }

    private String ensureTrailingSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    /** 清空 Redis 目录列表缓存 */
    private void clearListCache(Long parentId) {
        redisTemplate.delete("file_list:" + parentId);
    }

    /** 实体转 VO */
    private FileMetaVO toVO(FileMeta entity) {
        FileMetaVO vo = new FileMetaVO();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setOwnerId(entity.getOwnerId());
        vo.setParentId(entity.getParentId());
        vo.setName(entity.getName());
        vo.setPath(entity.getPath());
        vo.setIsDir(entity.getIsDir());
        vo.setSize(entity.getSize());
        vo.setHash(entity.getHash());
        vo.setMimeType(entity.getMimeType());
        vo.setStatus(entity.getStatus());
        vo.setDeletedAt(entity.getDeletedAt());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
