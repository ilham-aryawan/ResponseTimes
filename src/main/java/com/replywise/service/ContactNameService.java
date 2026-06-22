package com.replywise.service;

import com.replywise.model.RawMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class ContactNameService {
    private final AtomicReference<Map<String, String>> aliases = new AtomicReference<>(Map.of());

    public int importVcard(byte[] bytes) {
        var imported = parseVcards(new String(bytes, StandardCharsets.UTF_8));
        if (imported.aliases().isEmpty()) {
            return 0;
        }

        var next = new LinkedHashMap<>(aliases.get());
        next.putAll(imported.aliases());
        aliases.set(Map.copyOf(next));
        return imported.contactCount();
    }

    public List<RawMessage> resolveMessages(List<RawMessage> messages) {
        return messages.stream()
            .map(message -> new RawMessage(
                resolveLabel(message.contact()),
                message.timestamp(),
                message.fromMe()
            ))
            .toList();
    }

    public String resolveLabel(String label) {
        if (label == null || label.isBlank()) {
            return Objects.toString(label, "");
        }

        var parts = label.split("\\s*,\\s*");
        if (parts.length > 1) {
            var changed = false;
            var resolved = new ArrayList<String>();
            for (var part : parts) {
                var name = resolveSingleHandle(part);
                changed = changed || !name.equals(part);
                resolved.add(name);
            }
            if (changed) {
                return String.join(", ", resolved);
            }
        }

        return resolveSingleHandle(label);
    }

    private ImportedContacts parseVcards(String text) {
        var cards = new ArrayList<List<String>>();
        var current = new ArrayList<String>();
        var inCard = false;

        for (var line : unfoldLines(text)) {
            if (line.equalsIgnoreCase("BEGIN:VCARD")) {
                current = new ArrayList<>();
                inCard = true;
            } else if (line.equalsIgnoreCase("END:VCARD")) {
                if (inCard) {
                    cards.add(current);
                }
                inCard = false;
            } else if (inCard) {
                current.add(line);
            }
        }

        var aliasMap = new LinkedHashMap<String, String>();
        var contactCount = 0;
        for (var card : cards) {
            var name = "";
            var structuredName = "";
            var handles = new ArrayList<String>();

            for (var line : card) {
                var separator = line.indexOf(':');
                if (separator < 0) {
                    continue;
                }

                var property = line.substring(0, separator).split(";", 2)[0].toUpperCase(Locale.ROOT);
                var value = unescape(line.substring(separator + 1).trim());
                if (property.equals("FN")) {
                    name = value;
                } else if (property.equals("N")) {
                    structuredName = structuredName(value);
                } else if (property.equals("TEL") || property.equals("EMAIL")) {
                    handles.add(value);
                }
            }

            var displayName = name.isBlank() ? structuredName : name;
            if (displayName.isBlank() || handles.isEmpty()) {
                continue;
            }

            var added = false;
            for (var handle : handles) {
                for (var alias : aliasesFor(handle)) {
                    aliasMap.put(alias, displayName);
                    added = true;
                }
            }
            if (added) {
                contactCount++;
            }
        }

        return new ImportedContacts(aliasMap, contactCount);
    }

    private List<String> unfoldLines(String text) {
        var lines = new ArrayList<String>();
        for (var rawLine : text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            if ((rawLine.startsWith(" ") || rawLine.startsWith("\t")) && !lines.isEmpty()) {
                var last = lines.remove(lines.size() - 1);
                lines.add(last + rawLine.substring(1));
            } else {
                lines.add(rawLine.trim());
            }
        }
        return lines;
    }

    private String resolveSingleHandle(String handle) {
        for (var alias : aliasesFor(handle)) {
            var name = aliases.get().get(alias);
            if (name != null) {
                return name;
            }
        }
        return handle;
    }

    private List<String> aliasesFor(String handle) {
        var normalized = handle.trim()
            .replaceAll("^tel:", "")
            .replaceAll("^mailto:", "");
        var aliases = new ArrayList<String>();

        if (normalized.contains("@")) {
            aliases.add(normalized.toLowerCase(Locale.ROOT));
        }

        var digits = normalized.replaceAll("[^0-9]", "");
        if (!digits.isBlank()) {
            aliases.add(digits);
            aliases.add("+" + digits);
            if (digits.length() == 11 && digits.startsWith("1")) {
                aliases.add(digits.substring(1));
            } else if (digits.length() == 10) {
                aliases.add("1" + digits);
                aliases.add("+1" + digits);
            }
        }

        return aliases.stream().distinct().toList();
    }

    private String structuredName(String value) {
        var parts = value.split(";", -1);
        var family = parts.length > 0 ? parts[0].trim() : "";
        var given = parts.length > 1 ? parts[1].trim() : "";
        return (given + " " + family).trim();
    }

    private String unescape(String value) {
        return value
            .replace("\\n", " ")
            .replace("\\N", " ")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
            .trim();
    }

    private record ImportedContacts(Map<String, String> aliases, int contactCount) {}
}
