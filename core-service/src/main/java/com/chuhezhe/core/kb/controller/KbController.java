package com.chuhezhe.core.kb.controller;

import com.chuhezhe.common.result.Result;
import com.chuhezhe.core.kb.dto.KbProgressVO;
import com.chuhezhe.core.kb.dto.TriggerVectorizeRequest;
import com.chuhezhe.core.kb.service.KbService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KbController {

    private final KbService kbService;

    @PostMapping("/index")
    public Result<Long> trigger(@Valid @RequestBody TriggerVectorizeRequest request) {
        return Result.ok(kbService.triggerVectorize(request.getFileId()));
    }

    @GetMapping("/progress")
    public Result<List<KbProgressVO>> progressList() {
        return Result.ok(kbService.getProgress());
    }

    @GetMapping("/progress/{kbId}")
    public Result<KbProgressVO> progressDetail(@PathVariable Long kbId) {
        return Result.ok(kbService.getProgressDetail(kbId));
    }
}
