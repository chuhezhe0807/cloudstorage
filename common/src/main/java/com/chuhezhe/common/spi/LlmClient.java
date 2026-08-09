package com.chuhezhe.common.spi;

import com.chuhezhe.common.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class LlmClient {

    private final LlmProperties llmProperties;
    private final WebClient webClient;

    public LlmClient(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
        this.webClient = WebClient.builder()
                .baseUrl(llmProperties.getBaseUrl())
                .build();
    }

    @SuppressWarnings("unchecked")
    public ChatResponse chat(List<Map<String, String>> messages, double temperature) {
        Map<String, Object> body = Map.of(
                "model", llmProperties.getChatModel(),
                "messages", messages,
                "temperature", temperature
        );

        Map<String, Object> response = webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + llmProperties.getApiKey())
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");
            Map<String, Object> usage = (Map<String, Object>) response.get("usage");
            int promptTokens = usage != null ? ((Number) usage.get("prompt_tokens")).intValue() : 0;
            int completionTokens = usage != null ? ((Number) usage.get("completion_tokens")).intValue() : 0;
            return new ChatResponse(content, promptTokens, completionTokens);
        }
        return new ChatResponse("", 0, 0);
    }

    public ChatResponse chat(List<Map<String, String>> messages) {
        return chat(messages, 0.7);
    }

    public record ChatResponse(String content, int promptTokens, int completionTokens) {
    }
}
