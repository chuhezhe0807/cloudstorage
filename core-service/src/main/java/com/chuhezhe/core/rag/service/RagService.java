package com.chuhezhe.core.rag.service;

import com.chuhezhe.common.context.TenantContext;
import com.chuhezhe.common.spi.EmbeddingProvider;
import com.chuhezhe.common.spi.LangfuseTraceHelper;
import com.chuhezhe.common.spi.LlmClient;
import com.chuhezhe.common.exception.BusinessException;
import com.chuhezhe.common.result.ErrorCode;
import com.chuhezhe.core.file.entity.FileMeta;
import com.chuhezhe.core.file.mapper.FileMetaMapper;
import com.chuhezhe.core.kb.entity.KbChunk;
import com.chuhezhe.core.kb.entity.KnowledgeBase;
import com.chuhezhe.core.kb.mapper.KbChunkMapper;
import com.chuhezhe.core.kb.mapper.KnowledgeBaseMapper;
import com.chuhezhe.core.rag.dto.ChatRequest;
import com.chuhezhe.core.rag.dto.ChatResponse;
import com.chuhezhe.core.rag.graph.StateGraph;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private static final int TOP_K = 5;
    private static final int RERANK_TOP = 3;
    private static final double COSINE_THRESHOLD = 0.3;
    private static final int MAX_HISTORY = 10;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KbChunkMapper kbChunkMapper;
    private final FileMetaMapper fileMetaMapper;
    private final EmbeddingProvider embeddingProvider;
    private final LlmClient llmClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LangfuseTraceHelper langfuseTraceHelper;

    public ChatResponse chat(ChatRequest request) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            tenantId = 0L;
        }

        KnowledgeBase kb = knowledgeBaseMapper.selectById(request.getKbId());
        if (kb == null) {
            throw new BusinessException(ErrorCode.KB_NOT_FOUND);
        }
        if (!tenantId.equals(kb.getTenantId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!"ready".equals(kb.getStatus()) && !"partial".equals(kb.getStatus())) {
            throw new BusinessException(ErrorCode.KB_NOT_READY);
        }

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        LangfuseTraceHelper.LangfuseTrace trace = langfuseTraceHelper.startTrace("rag-chat-" + sessionId, request.getMessage());

        try {
            StateGraph graph = buildGraph(kb, tenantId);
            Map<String, Object> state = new HashMap<>();
            state.put("query", request.getMessage());
            state.put("kbId", kb.getId());
            state.put("tenantId", tenantId);
            state.put("sessionId", sessionId);
            state.put("startTime", Instant.now());

            Map<String, Object> result = graph.invoke(state);
            trace.close();

            @SuppressWarnings("unchecked")
            List<ChatResponse.Citation> citations = (List<ChatResponse.Citation>) result.getOrDefault("citations", List.of());
            String answer = (String) result.getOrDefault("answer", "");
            addToHistory(sessionId, request.getMessage(), answer);

            ChatResponse response = new ChatResponse();
            response.setAnswer(answer);
            response.setSessionId(sessionId);
            response.setCitations(citations);
            return response;
        } catch (Exception e) {
            trace.close();
            log.error("RAG chat failed: kbId={}", request.getKbId(), e);
            throw new BusinessException(ErrorCode.LLM_CALL_FAILED);
        }
    }

    private StateGraph buildGraph(KnowledgeBase kb, Long tenantId) {
        StateGraph graph = new StateGraph();

        graph.addNode("retrieve", state -> {
            @SuppressWarnings("unchecked")
            String query = (String) state.get("query");
            float[] queryVec = embeddingProvider.embed(query);
            String vectorStr = toVectorString(queryVec);
            List<KbChunk> chunks = kbChunkMapper.searchTopK(kb.getId(), vectorStr, TOP_K);

            state.put("chunks", chunks);
            return state;
        });

        graph.addNode("rerank", state -> {
            @SuppressWarnings("unchecked")
            List<KbChunk> chunks = (List<KbChunk>) state.get("chunks");
            float[] queryVec = embeddingProvider.embed((String) state.get("query"));

            List<KbChunk> reranked = new ArrayList<>();
            for (KbChunk chunk : chunks) {
                float[] chunkVec = chunk.getEmbedding();
                if (chunkVec != null) {
                    double sim = cosineSimilarity(queryVec, chunkVec);
                    if (sim >= COSINE_THRESHOLD && reranked.size() < RERANK_TOP) {
                        reranked.add(chunk);
                    }
                }
            }

            state.put("rerankedChunks", reranked);
            return state;
        });

        graph.addNode("generate", state -> {
            @SuppressWarnings("unchecked")
            List<KbChunk> reranked = (List<KbChunk>) state.get("rerankedChunks");
            String query = (String) state.get("query");
            String history = getHistory((String) state.get("sessionId"));

            StringBuilder context = new StringBuilder();
            for (KbChunk chunk : reranked) {
                context.append("---\n").append(chunk.getContent()).append("\n");
            }

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content",
                    "You are a helpful assistant. Answer the user's question based on the provided context. "
                            + "If the context does not contain relevant information, say so. "
                            + "Include references to source documents in your answer."));

            if (history != null && !history.isEmpty()) {
                messages.addAll(parseHistory(history));
            }

            String prompt = String.format("""
                    Context:
                    %s
                    
                    Question: %s
                    """, context.toString(), query);

            messages.add(Map.of("role", "user", "content", prompt));

            long startMs = System.currentTimeMillis();
            LlmClient.ChatResponse llmResp = llmClient.chat(messages);
            long elapsedMs = System.currentTimeMillis() - startMs;

            state.put("answer", llmResp.content());
            state.put("tokenUsage", Map.of("promptTokens", llmResp.promptTokens(),
                    "completionTokens", llmResp.completionTokens(), "elapsedMs", elapsedMs));
            return state;
        });

        graph.addNode("cite", state -> {
            @SuppressWarnings("unchecked")
            List<KbChunk> reranked = (List<KbChunk>) state.get("rerankedChunks");
            List<ChatResponse.Citation> citations = new ArrayList<>();
            for (KbChunk chunk : reranked) {
                if (chunk.getFileId() != null) {
                    FileMeta file = fileMetaMapper.selectById(chunk.getFileId());
                    if (file != null) {
                        ChatResponse.Citation citation = new ChatResponse.Citation();
                        citation.setFileId(file.getId());
                        citation.setFileName(file.getName());
                        String snippet = chunk.getContent();
                        if (snippet.length() > 200) {
                            snippet = snippet.substring(0, 200) + "...";
                        }
                        citation.setSnippet(snippet);
                        citations.add(citation);
                    }
                }
            }
            state.put("citations", citations);
            return state;
        });

        graph.setEntryPoint("retrieve");
        graph.addEdge("retrieve", "rerank");
        graph.addEdge("rerank", "generate");
        graph.addEdge("generate", "cite");
        graph.setFinishPoint("cite");

        return graph;
    }

    private void addToHistory(String sessionId, String userMsg, String assistantMsg) {
        try {
            String key = "rag:session:" + sessionId;
            String entry = objectMapper.writeValueAsString(Map.of(
                    "user", userMsg, "assistant", assistantMsg, "ts", Instant.now().toString()
            ));
            redisTemplate.opsForList().rightPush(key, entry);
            redisTemplate.expire(key, Duration.ofHours(1));
            Long size = redisTemplate.opsForList().size(key);
            if (size != null && size > MAX_HISTORY) {
                redisTemplate.opsForList().trim(key, -MAX_HISTORY, -1);
            }
        } catch (Exception e) {
            log.warn("Failed to save chat history: sessionId={}", sessionId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private String getHistory(String sessionId) {
        try {
            String key = "rag:session:" + sessionId;
            List<String> entries = redisTemplate.opsForList().range(key, 0, -1);
            if (entries == null || entries.isEmpty()) {
                return "";
            }
            return String.join("\n", entries);
        } catch (Exception e) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseHistory(String historyStr) {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            String[] entries = historyStr.split("\n");
            for (String entry : entries) {
                if (entry.isEmpty()) {
                    continue;
                }
                Map<String, String> map = objectMapper.readValue(entry, new TypeReference<Map<String, String>>() {});
                result.add(Map.of("role", "user", "content", map.get("user")));
                result.add(Map.of("role", "assistant", "content", map.get("assistant")));
            }
        } catch (Exception e) {
            log.warn("Failed to parse history", e);
        }
        return result;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        normA = Math.sqrt(normA);
        normB = Math.sqrt(normB);
        return (normA > 0 && normB > 0) ? dot / (normA * normB) : 0;
    }

    private String toVectorString(float[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(values[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    public List<KnowledgeBase> getAvailableKbs() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            tenantId = 0L;
        }
        return knowledgeBaseMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getTenantId, tenantId)
                        .in(KnowledgeBase::getStatus, List.of("ready", "partial"))
                        .orderByDesc(KnowledgeBase::getCreatedAt)
        );
    }
}
