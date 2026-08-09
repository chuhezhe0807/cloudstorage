package com.chuhezhe.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private String provider = "openai";
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey;
    private String chatModel = "gpt-4o-mini";
    private String embeddingModel = "text-embedding-ada-002";
    private int embeddingDimension = 1536;
}
