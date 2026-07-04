package com.aimv.infrastructure.database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.postgresql.util.PGobject;

public final class PostgresJsonSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP_TYPE = new TypeReference<>() {
    };

    private PostgresJsonSupport() {
    }

    public static PGobject jsonb(Object value) {
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        try {
            jsonb.setValue(OBJECT_MAPPER.writeValueAsString(value));
        } catch (JsonProcessingException | SQLException exception) {
            throw new IllegalArgumentException("JSONB 序列化失败", exception);
        }
        return jsonb;
    }

    public static List<String> stringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSONB 字符串列表解析失败", exception);
        }
    }

    public static Map<String, Object> objectMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, OBJECT_MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSONB 对象解析失败", exception);
        }
    }
}
