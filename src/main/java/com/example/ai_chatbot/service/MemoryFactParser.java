package com.example.ai_chatbot.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Forgiving parser for the small local model's "JSON" output. Small models often wrap the array in
 * markdown fences, add commentary, or emit slightly broken JSON; instead of failing on the whole
 * response we simply pull out every {"key": ..., "value": ...} pair we can find.
 */
public final class MemoryFactParser {

    public record Fact(String key, String value) {
    }

    private static final Pattern PAIR = Pattern.compile(
            "\"key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*,\\s*\"value\"\\s*:\\s*"
                    + "(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([\\w.\\-]+))",
            Pattern.DOTALL);

    private MemoryFactParser() {
    }

    public static List<Fact> parse(String raw) {
        List<Fact> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        Matcher m = PAIR.matcher(raw);
        while (m.find()) {
            String value = m.group(2) != null ? m.group(2) : m.group(3);
            out.add(new Fact(unescape(m.group(1)), unescape(value)));
        }
        return out;
    }

    private static String unescape(String s) {
        return s.replace("\\n", " ").replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
