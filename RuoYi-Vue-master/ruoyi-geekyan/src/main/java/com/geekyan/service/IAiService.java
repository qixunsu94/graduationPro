package com.geekyan.service;

import java.util.Map;

public interface IAiService {
    String chat(String systemPrompt, String userMessage, String sessionId);
    String translate(String text, String sourceLang, String targetLang);
    String analyzeGrammar(String text);
    String generateGreeting(String role, String topic);
    Map<String, Object> practice(String userMessage, String aiMessage);
}
