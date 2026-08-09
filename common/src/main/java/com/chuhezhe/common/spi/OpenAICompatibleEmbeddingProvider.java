package com.chuhezhe.common.spi;

import com.chuhezhe.common.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "llm", name = "api-key")
public class OpenAICompatibleEmbeddingProvider implements EmbeddingProvider {

    private final LlmProperties llmProperties;
    private final WebClient webClient;

    public OpenAICompatibleEmbeddingProvider(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
        this.webClient = WebClient.builder()
                .baseUrl(llmProperties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer ***")
                .build();
    }

    @Override
    public float[] embed(String text) {
        List<float[]> results = doEmbed(List.of(text));
        return results.isEmpty() ? new float[0] : results.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> all = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += 100) {
            int end = Math.min(i + 100, texts.size());
            all.addAll(doEmbed(texts.subList(i, end)));
        }
        return all;
    }

    @Override
    public int dimension() {
        return llmProperties.getEmbeddingDimension();
    }

    @SuppressWarnings("unchecked")
    private List<float[]> doEmbed(List<String> texts) {
        Map<String, Object> body = Map.of(
                "model", llmProperties.getEmbeddingModel(),
                "input", texts
        );

        Map<String, Object> response = webClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + llmProperties.getApiKey())
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        List<float[]> embeddings = new ArrayList<>();
        if (data != null) {
            for (Map<String, Object> item : data) {
                List<Double> embeddingList = (List<Double>) item.get("embedding");
                if (embeddingList != null) {
                    float[] vec = new float[embeddingList.size()];
                    for (int j = 0; j < embeddingList.size(); j++) {
                        vec[j] = embeddingList.get(j).floatValue();
                    }
                    embeddings.add(vec);
                }
            }
        }
        log.debug("Embedding batch completed: {} texts, {} vectors", texts.size(), embeddings.size());
        return embeddings;
    }
}
