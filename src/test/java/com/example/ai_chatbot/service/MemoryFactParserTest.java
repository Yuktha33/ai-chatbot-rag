package com.example.ai_chatbot.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryFactParserTest {

    @Test
    void parsesCleanArray() {
        List<MemoryFactParser.Fact> f = MemoryFactParser.parse("[{\"key\":\"name\",\"value\":\"Yuktha\"}]");
        assertEquals(1, f.size());
        assertEquals("name", f.get(0).key());
        assertEquals("Yuktha", f.get(0).value());
    }

    @Test
    void survivesMarkdownFencesAndChatter() {
        String raw = "Sure! Here you go:\n```json\n[{\"key\": \"skills\", \"value\": \"Java\"},\n"
                + " {\"key\": \"goal\", \"value\": \"get a backend job\"}]\n```";
        List<MemoryFactParser.Fact> f = MemoryFactParser.parse(raw);
        assertEquals(2, f.size());
        assertEquals("get a backend job", f.get(1).value());
    }

    @Test
    void acceptsNumbersAndEscapedQuotes() {
        List<MemoryFactParser.Fact> f = MemoryFactParser.parse(
                "[{\"key\":\"age\",\"value\":21},{\"key\":\"motto\",\"value\":\"say \\\"hi\\\"\"}]");
        assertEquals("21", f.get(0).value());
        assertEquals("say \"hi\"", f.get(1).value());
    }

    @Test
    void emptyOrGarbageGivesNoFacts() {
        assertTrue(MemoryFactParser.parse("[]").isEmpty());
        assertTrue(MemoryFactParser.parse(null).isEmpty());
        assertTrue(MemoryFactParser.parse("I don't know").isEmpty());
        assertTrue(MemoryFactParser.parse("[\"just a string\"]").isEmpty());
    }
}
