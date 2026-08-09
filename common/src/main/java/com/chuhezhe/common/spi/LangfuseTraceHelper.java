package com.chuhezhe.common.spi;

import com.chuhezhe.common.config.LangfuseProperties;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class LangfuseTraceHelper {

    private final LangfuseProperties properties;

    public LangfuseTraceHelper(LangfuseProperties properties) {
        this.properties = properties;
    }

    public LangfuseTrace startTrace(String name, String input) {
        return createTrace(name, input);
    }

    @Nullable
    private LangfuseTrace createTrace(String name, String input) {
        if (!properties.isEnabled() || properties.getPublicKey() == null || properties.getSecretKey() == null) {
            log.debug("Langfuse disabled or not configured, skipping trace");
            return new NoOpTrace();
        }
        try {
            String traceId = UUID.randomUUID().toString();
            log.debug("Langfuse trace started: name={}, traceId={}, input={}", name, traceId, input);
            return new LangfuseTraceImpl(traceId, name);
        } catch (Exception e) {
            log.warn("Failed to create langfuse trace: {}", e.getMessage());
            return new NoOpTrace();
        }
    }

    public interface LangfuseTrace extends AutoCloseable {
        void span(String name, Map<String, Object> metadata);
        void update(String output, Map<String, Object> metadata);
        void close();
    }

    private static class LangfuseTraceImpl implements LangfuseTrace {
        private final String traceId;
        private final String name;

        LangfuseTraceImpl(String traceId, String name) {
            this.traceId = traceId;
            this.name = name;
        }

        @Override
        public void span(String spanName, Map<String, Object> metadata) {
            log.debug("Langfuse span: trace={}, span={}, metadata={}", traceId, spanName, metadata);
        }

        @Override
        public void update(String output, Map<String, Object> metadata) {
            log.debug("Langfuse trace update: trace={}, output={}, metadata={}", traceId, output, metadata);
        }

        @Override
        public void close() {
            log.debug("Langfuse trace completed: trace={}, name={}", traceId, name);
        }
    }

    private static class NoOpTrace implements LangfuseTrace {
        @Override
        public void span(String name, Map<String, Object> metadata) {
        }

        @Override
        public void update(String output, Map<String, Object> metadata) {
        }

        @Override
        public void close() {
        }
    }
}
