package com.chuhezhe.core.rag.controller;

import com.chuhezhe.common.result.Result;
import com.chuhezhe.core.kb.entity.KnowledgeBase;
import com.chuhezhe.core.rag.dto.ChatRequest;
import com.chuhezhe.core.rag.dto.ChatResponse;
import com.chuhezhe.core.rag.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @PostMapping("/chat")
    public Result<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return Result.ok(ragService.chat(request));
    }

    @GetMapping("/kbs")
    public Result<List<KnowledgeBase>> availableKbs() {
        return Result.ok(ragService.getAvailableKbs());
    }
}
