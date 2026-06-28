package com.chuhezhe.core.file.service;

import com.chuhezhe.common.dto.PageResult;
import com.chuhezhe.core.file.dto.*;
import com.chuhezhe.core.file.entity.FileMeta;

import java.util.List;

public interface FileService {

    /** 创建目录 */
    FileMetaVO createDirectory(CreateDirRequest request);

    /** 文件列表 */
    List<FileMetaVO> listFiles(Long parentId);

    /** 重命名 */
    FileMetaVO rename(Long fileId, RenameRequest request);

    /** 移动文件/目录 */
    FileMetaVO move(Long fileId, MoveRequest request);

    /** 软删除（入回收站） */
    void softDelete(Long fileId);

    /** 回收站列表 */
    PageResult<FileMetaVO> listRecycled(int page, int size);

    /** 从回收站还原 */
    void restore(Long fileId);

    /** 永久删除 */
    void permanentDelete(Long fileId);

    /** 搜索 */
    PageResult<FileMetaVO> search(FileSearchRequest request);

    /** 定时清理过期回收站 */
    int cleanExpiredRecycled(int batchSize);
}
