package com.chuhezhe.core.share.controller;

import com.chuhezhe.common.result.Result;
import com.chuhezhe.core.share.dto.*;
import com.chuhezhe.core.share.service.ShareService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @PostMapping
    public Result<ShareVO> create(@Valid @RequestBody CreateShareRequest request) {
        return Result.ok(shareService.create(request));
    }

    @GetMapping("/{code}/info")
    public Result<ShareInfoResponse> info(@PathVariable String code) {
        return Result.ok(shareService.getShareInfo(code));
    }

    @PostMapping("/{code}/access")
    public Result<ShareAccessResponse> access(@PathVariable String code,
                                               @RequestBody(required = false) ShareAccessRequest request) {
        if (request == null) {
            request = new ShareAccessRequest();
        }
        return Result.ok(shareService.access(code, request));
    }

    @PostMapping("/{code}/download")
    public void download(@PathVariable String code,
                         @RequestBody ShareDownloadRequest request,
                         HttpServletResponse response) throws IOException {
        byte[] zipData = shareService.downloadFiles(code, request.getFileIds());

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String filename = "share_" + code + "_" + timestamp + ".zip";

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setContentLength(zipData.length);
        response.getOutputStream().write(zipData);
        response.getOutputStream().flush();
    }

    @GetMapping
    public Result<List<ShareVO>> listMyShares() {
        return Result.ok(shareService.listMyShares());
    }

    @DeleteMapping("/{id}")
    public Result<Void> cancel(@PathVariable Long id) {
        shareService.cancel(id);
        return Result.ok();
    }

    @PatchMapping("/{id}")
    public Result<ShareVO> update(@PathVariable Long id, @RequestBody UpdateShareRequest request) {
        return Result.ok(shareService.update(id, request));
    }
}
