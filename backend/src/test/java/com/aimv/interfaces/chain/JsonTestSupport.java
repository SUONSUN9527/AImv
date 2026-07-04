package com.aimv.interfaces.chain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonTestSupport() {
    }

    public static String extractString(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Missing JSON field: " + fieldName + " in " + json);
        }
        return matcher.group(1);
    }

    public static String extractFirstArrayString(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\\[\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Missing JSON array field: " + fieldName + " in " + json);
        }
        return matcher.group(1);
    }

    public static String extractStageString(String json, String stageCode, String fieldName) {
        JsonNode stage = stageNode(json, stageCode);
        JsonNode value = stage.get(fieldName);
        if (value == null || !value.isTextual()) {
            throw new AssertionError("Missing JSON stage field: " + fieldName + " in " + stage);
        }
        return value.asText();
    }

    public static String extractLastStageString(String json, String stageCode, String fieldName) {
        JsonNode stage = lastStageNode(json, stageCode);
        JsonNode value = stage.get(fieldName);
        if (value == null || !value.isTextual()) {
            throw new AssertionError("Missing JSON stage field: " + fieldName + " in " + stage);
        }
        return value.asText();
    }

    public static String extractStageFirstArrayString(String json, String stageCode, String fieldName) {
        JsonNode value = stageNode(json, stageCode).get(fieldName);
        if (value == null || !value.isArray() || value.isEmpty() || !value.get(0).isTextual()) {
            throw new AssertionError("Missing JSON stage array field: " + fieldName + " in " + json);
        }
        return value.get(0).asText();
    }

    public static JsonNode readTree(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (IOException exception) {
            throw new AssertionError("Invalid JSON: " + json, exception);
        }
    }

    private static JsonNode stageNode(String json, String stageCode) {
        try {
            for (JsonNode stage : OBJECT_MAPPER.readTree(json).at("/data/stageRuns")) {
                if (stageCode.equals(stage.path("stageCode").asText())) {
                    return stage;
                }
            }
        } catch (IOException exception) {
            throw new AssertionError("Invalid JSON: " + json, exception);
        }
        throw new AssertionError("Missing JSON stage: " + stageCode + " in " + json);
    }

    private static JsonNode lastStageNode(String json, String stageCode) {
        try {
            JsonNode lastMatch = null;
            for (JsonNode stage : OBJECT_MAPPER.readTree(json).at("/data/stageRuns")) {
                if (stageCode.equals(stage.path("stageCode").asText())) {
                    lastMatch = stage;
                }
            }
            if (lastMatch != null) {
                return lastMatch;
            }
        } catch (IOException exception) {
            throw new AssertionError("Invalid JSON: " + json, exception);
        }
        throw new AssertionError("Missing JSON stage: " + stageCode + " in " + json);
    }
}
