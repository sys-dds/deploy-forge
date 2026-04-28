package com.deployforge.api.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.postgresql.util.PGobject;

public final class Jsonb {

    public static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private Jsonb() {
    }

    public static JsonNode emptyObjectIfNull(JsonNode node) {
        return node == null || node.isNull() ? MAPPER.createObjectNode() : node;
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    public static PGobject toPgObject(JsonNode node) {
        try {
            PGobject object = new PGobject();
            object.setType("jsonb");
            object.setValue(emptyObjectIfNull(node).toString());
            return object;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JSON metadata", exception);
        }
    }

    public static JsonNode fromString(String value) {
        try {
            return value == null ? object() : MAPPER.readTree(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JSON from database", exception);
        }
    }
}
