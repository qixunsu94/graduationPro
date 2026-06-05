package com.geekyan.service;

import java.util.Map;

public interface IAiService {
    String chat(String systemPrompt, String userMessage, String sessionId);

    String chatWithoutHistory(String systemPrompt, String userMessage);

    String getModePrompt(String mode);

    String chatWithMode(String mode, String userMessage, String sessionId);

    String chatWithImage(String mode, String userMessage, String imageBase64, String sessionId);

    String chatWithImagePrompt(String systemPrompt, String userMessage, String imageBase64, String sessionId);

    String translate(String text, String sourceLang, String targetLang);

    String analyzeGrammar(String text);

    String generateGreeting(String role, String topic);

    Map<String, Object> practice(String userMessage, String aiMessage);

    String searchWord(String word);

    String searchChineseWord(String word);

    String fuzzySearchWords(String keyword);
}
