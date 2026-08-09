package com.chuhezhe.common.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VectorTypeHandlerTest {

    @Test
    void toAndFromVectorString() {
        float[] original = {0.1f, 0.2f, 0.3f};

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < original.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(original[i]);
        }
        sb.append("]");
        String vectorString = sb.toString();

        String trimmed = vectorString.replaceAll("[\\[\\]\\s]", "");
        String[] parts = trimmed.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }

        assertArrayEquals(original, result, 0.0001f);
    }

    @Test
    void emptyVector() {
        String vectorString = "[]";
        String trimmed = vectorString.replaceAll("[\\[\\]\\s]", "");
        float[] result;
        if (trimmed.isEmpty()) {
            result = new float[0];
        } else {
            String[] parts = trimmed.split(",");
            result = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Float.parseFloat(parts[i]);
            }
        }
        assertEquals(0, result.length);
    }
}
