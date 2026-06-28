package com.chuhezhe.core.file.controller;

import com.chuhezhe.common.dto.PageResult;
import com.chuhezhe.common.result.Result;
import com.chuhezhe.core.file.dto.*;
import com.chuhezhe.core.file.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件接口：目录、重命名、移动、删除、回收站、搜索。
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /** 创建目录 */
    @PostMapping("/mkdir")
    public Result<FileMetaVO> createDirectory(@Valid @RequestBody CreateDirRequest request) {
        return Result.ok(fileService.createDirectory(request));
    }

    /** 文件/目录列表（按 parentId 查询） */
    @GetMapping
    public Result<List<FileMetaVO>> listFiles(@RequestParam(required = false) Long parentId) {
        return Result.ok(fileService.listFiles(parentId));
    }

    /** 重命名 */
    @PatchMapping("/{id}/rename")
    public Result<FileMetaVO> rename(@PathVariable Long id, @Valid @RequestBody RenameRequest request) {
        return Result.ok(fileService.rename(id, request));
    }

    /** 移动 */
    @PatchMapping("/{id}/move")
    public Result<FileMetaVO> move(@PathVariable Long id, @RequestBody MoveRequest request) {
        return Result.ok(fileService.move(id, request));
    }

    /** 软删除（入回收站） */
    @DeleteMapping("/{id}")
    public Result<Void> softDelete(@PathVariable Long id) {
        fileService.softDelete(id);
        return Result.ok();
    }

    /** 回收站列表 */
    @GetMapping("/recycle")
    public Result<PageResult<FileMetaVO>> listRecycled(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return Result.ok(fileService.listRecycled(page, size));
    }

    /** 从回收站还原 */
    @PutMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        fileService.restore(id);
        return Result.ok();
    }

    /** 永久删除 */
    @DeleteMapping("/{id}/permanent")
    public Result<Void> permanentDelete(@PathVariable Long id) {
        fileService.permanentDelete(id);
        return Result.ok();
    }

    /** 搜索 */
    @GetMapping("/search")
    public Result<PageResult<FileMetaVO>> search(@Valid FileSearchRequest request) {
        return Result.ok(fileService.search(request));
    }
}
