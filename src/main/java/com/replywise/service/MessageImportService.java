package com.replywise.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.replywise.model.RawMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class MessageImportService {
    private final ObjectMapper objectMapper;

    public MessageImportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<RawMessage> parse(String filename, byte[] bytes) throws IOException {
        var text = new String(bytes, StandardCharsets.UTF_8).trim();
        if (text.isBlank()) {
            return List.of();
        }
        if (looksLikeJson(filename, text)) {
            return parseJson(text);
        }
        return parseDelimited(text);
    }

    private List<RawMessage> parseJson(String text) throws IOException {
        var rows = objectMapper.readValue(text, new TypeReference<List<Map<String, Object>>>() {});
        var messages = new ArrayList<RawMessage>();
        for (var row : rows) {
            toRawMessage(normalizeKeys(row)).ifPresent(messages::add);
        }
        return messages;
    }

    private List<RawMessage> parseDelimited(String text) {
        var lines = Arrays.stream(text.split("\\R"))
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .toList();
        if (lines.size() < 2) {
            return List.of();
        }

        var headers = splitCsvLine(lines.get(0)).stream()
            .map(this::normalizeKey)
            .toList();
        var messages = new ArrayList<RawMessage>();

        for (var i = 1; i < lines.size(); i++) {
            var values = splitCsvLine(lines.get(i));
            var row = new LinkedHashMap<String, Object>();
            for (var j = 0; j < Math.min(headers.size(), values.size()); j++) {
                row.put(headers.get(j), values.get(j));
            }
            toRawMessage(row).ifPresent(messages::add);
        }
        return messages;
    }

    private java.util.Optional<RawMessage> toRawMessage(Map<String, Object> row) {
        var contact = firstPresent(row, "contact", "name", "participant", "handle", "chat", "conversation");
        var timestamp = firstPresent(row, "timestamp", "date", "sentat", "time", "datetime");
        var direction = firstPresent(row, "direction", "fromme", "isfromme", "sender", "type");

        if (contact == null || timestamp == null || direction == null) {
            return java.util.Optional.empty();
        }

        try {
            return java.util.Optional.of(new RawMessage(
                contact,
                parseInstant(timestamp),
                parseDirection(direction)
            ));
        } catch (RuntimeException ignored) {
            return java.util.Optional.empty();
        }
    }

    private Instant parseInstant(String value) {
        var trimmed = value.trim();
        if (trimmed.matches("\\d{13}")) {
            return Instant.ofEpochMilli(Long.parseLong(trimmed));
        }
        if (trimmed.matches("\\d{10}")) {
            return Instant.ofEpochSecond(Long.parseLong(trimmed));
        }
        try {
            return Instant.parse(trimmed);
        } catch (RuntimeException ignored) {
            // Try less strict but common local timestamp formats next.
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (RuntimeException ignored) {
            // Try local time without an offset.
        }
        return LocalDateTime.parse(trimmed).atZone(ZoneId.systemDefault()).toInstant();
    }

    private boolean parseDirection(String value) {
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("true")
            || normalized.equals("1")
            || normalized.equals("me")
            || normalized.equals("mine")
            || normalized.equals("outbound")
            || normalized.equals("sent")
            || normalized.equals("from_me");
    }

    private String firstPresent(Map<String, Object> row, String... keys) {
        for (var key : keys) {
            var value = row.get(key);
            if (value != null && !Objects.toString(value).isBlank()) {
                return Objects.toString(value).trim();
            }
        }
        return null;
    }

    private Map<String, Object> normalizeKeys(Map<String, Object> row) {
        var normalized = new LinkedHashMap<String, Object>();
        row.forEach((key, value) -> normalized.put(normalizeKey(key), value));
        return normalized;
    }

    private String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private boolean looksLikeJson(String filename, String text) {
        return text.startsWith("[") || (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".json"));
    }

    private List<String> splitCsvLine(String line) {
        var values = new ArrayList<String>();
        var current = new StringBuilder();
        var quoted = false;
        for (var i = 0; i < line.length(); i++) {
            var character = line.charAt(i);
            if (character == '"') {
                quoted = !quoted;
            } else if (character == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        values.add(current.toString().trim());
        return values;
    }
}
