package com.chuhezhe.core.rag.graph;

import java.util.Map;
import java.util.function.Function;

public class StateGraph {

    private final Map<String, NodeDef> nodes = new java.util.LinkedHashMap<>();
    private String entryNode;
    private String finishNode;

    public StateGraph addNode(String name, Function<Map<String, Object>, Map<String, Object>> handler) {
        nodes.put(name, new NodeDef(handler));
        return this;
    }

    public StateGraph setEntryPoint(String nodeName) {
        this.entryNode = nodeName;
        return this;
    }

    public StateGraph setFinishPoint(String nodeName) {
        this.finishNode = nodeName;
        return this;
    }

    public StateGraph addEdge(String from, String to) {
        NodeDef fromNode = nodes.get(from);
        if (fromNode != null) {
            fromNode.next = to;
        }
        return this;
    }

    public Map<String, Object> invoke(Map<String, Object> initialState) {
        String currentNode = entryNode;
        Map<String, Object> state = initialState;

        while (currentNode != null && !currentNode.equals(finishNode)) {
            NodeDef nodeDef = nodes.get(currentNode);
            if (nodeDef == null) {
                break;
            }
            state = nodeDef.handler.apply(state);
            currentNode = nodeDef.next;
        }

        if (finishNode != null && nodes.containsKey(finishNode)) {
            state = nodes.get(finishNode).handler.apply(state);
        }

        return state;
    }

    private static class NodeDef {
        final Function<Map<String, Object>, Map<String, Object>> handler;
        String next;

        NodeDef(Function<Map<String, Object>, Map<String, Object>> handler) {
            this.handler = handler;
        }
    }
}
