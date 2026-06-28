package com.chuhezhe.core.share.controller;

import com.chuhezhe.common.result.Result;
import com.chuhezhe.core.share.dto.*;
import com.chuhezhe.core.share.service.ShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分享接口：创建、访问、管理。
 */
@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    /** 创建分享链接（需登录） */
    @PostMapping
    public Result<ShareVO> create(@Valid @RequestBody CreateShareRequest request) {
        return Result.ok(shareService.create(request));
    }

    /** 访问分享（无需登录，网关白名单放行） */
    @PostMapping("/{code}/access")
    public Result<ShareAccessResponse> access(@PathVariable String code,
                                               @RequestBody(required = false) ShareAccessRequest request) {
        if (request == null) {
            request = new ShareAccessRequest();
        }
        return Result.ok(shareService.access(code, request));
    }

    /** 我的分享列表（需登录） */
    @GetMapping
    public Result<List<ShareVO>> listMyShares() {
        return Result.ok(shareService.listMyShares());
    }

    /** 取消分享 */
    @DeleteMapping("/{id}")
    public Result<Void> cancel(@PathVariable Long id) {
        shareService.cancel(id);
        return Result.ok();
    }

    /** 更新分享设置 */
    @PatchMapping("/{id}")
    public Result<ShareVO> update(@PathVariable Long id, @RequestBody UpdateShareRequest request) {
        return Result.ok(shareService.update(id, request));
    }
}
