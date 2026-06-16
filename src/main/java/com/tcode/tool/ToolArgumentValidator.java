package com.tcode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class ToolArgumentValidator {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolArgumentValidator() {
    }

    static ValidationResult validate(JsonNode schema, String argumentsJson) {
        JsonNode args;
        try {
            args = argumentsJson == null || argumentsJson.isBlank()
                    ? MAPPER.createObjectNode()
                    : MAPPER.readTree(argumentsJson);
        } catch (IOException e) {
            return ValidationResult.invalid("arguments must be valid JSON: " + e.getMessage());
        }
        return validate(schema, args);
    }

    static ValidationResult validate(JsonNode schema, JsonNode args) {
        if (schema == null || schema.isMissingNode() || schema.isNull()) {
            return ValidationResult.valid(args);
        }
        List<String> errors = new ArrayList<>();
        validateNode("$", schema, args, errors);
        if (!errors.isEmpty()) {
            return ValidationResult.invalid(String.join("; ", errors));
        }
        return ValidationResult.valid(args);
    }

    private static void validateNode(String path, JsonNode schema, JsonNode value, List<String> errors) {
        String type = schema.path("type").asText("");
        if (!type.isBlank() && !matchesType(type, value)) {
            errors.add(path + " must be " + type);
            return;
        }
        if ("object".equals(type) || schema.has("properties") || schema.has("required")) {
            validateObject(path, schema, value, errors);
        }
        if (schema.has("enum") && schema.path("enum").isArray()) {
            boolean allowed = false;
            for (JsonNode item : schema.path("enum")) {
                if (item.equals(value)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                errors.add(path + " must match one of enum values");
            }
        }
    }

    private static void validateObject(String path, JsonNode schema, JsonNode value, List<String> errors) {
        if (!value.isObject()) {
            errors.add(path + " must be object");
            return;
        }
        JsonNode required = schema.path("required");
        if (required.isArray()) {
            for (JsonNode field : required) {
                String name = field.asText("");
                if (!name.isBlank() && !value.has(name)) {
                    errors.add(path + "." + name + " is required");
                }
            }
        }
        JsonNode properties = schema.path("properties");
        if (!properties.isObject()) {
            return;
        }
        properties.fields().forEachRemaining(entry -> {
            if (value.has(entry.getKey())) {
                validateNode(path + "." + entry.getKey(), entry.getValue(), value.get(entry.getKey()), errors);
            }
        });
    }

    private static boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber() || isIntegerString(value);
            case "number" -> value.isNumber() || isNumberString(value);
            case "boolean" -> value.isBoolean() || isBooleanString(value);
            case "null" -> value.isNull();
            default -> true;
        };
    }

    private static boolean isIntegerString(JsonNode value) {
        if (!value.isTextual()) {
            return false;
        }
        try {
            Integer.parseInt(value.asText().trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isNumberString(JsonNode value) {
        if (!value.isTextual()) {
            return false;
        }
        try {
            Double.parseDouble(value.asText().trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isBooleanString(JsonNode value) {
        if (!value.isTextual()) {
            return false;
        }
        String text = value.asText().trim();
        return "true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text);
    }

    record ValidationResult(boolean valid, JsonNode arguments, String errorMessage) {
        static ValidationResult valid(JsonNode arguments) {
            return new ValidationResult(true, arguments, "");
        }

        static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, null, errorMessage == null ? "" : errorMessage);
        }
    }
}
