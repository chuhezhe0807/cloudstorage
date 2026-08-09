package com.chuhezhe.core.rag.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatResponse {

    private String answer;
    private String sessionId;
    private List<Citation> citations;

    @Data
    public static class Citation {
        private Long fileId;
        private String fileName;
        private String snippet;
    }
}
