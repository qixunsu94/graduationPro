package com.geekyan.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.geekyan.service.IAiService;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AiServiceImpl implements IAiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    @Value("${geekyan.ai.api-key:}")
    private String apiKey;

    @Value("${geekyan.ai.api-url:https://open.bigmodel.cn/api/paas/v4/chat/completions}")
    private String apiUrl;

    @Value("${geekyan.ai.model:glm-4-flash}")
    private String model;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public String chat(String systemPrompt, String userMessage, String sessionId) {
        JSONArray messages = new JSONArray();
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        return callAiApi(messages);
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        JSONArray messages = new JSONArray();
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", "You are a professional translator. Translate the following text from " + sourceLang + " to " + targetLang + ". Only return the translation result.");
        messages.add(systemMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", text);
        messages.add(userMsg);

        return callAiApi(messages);
    }

    @Override
    public String analyzeGrammar(String text) {
        JSONArray messages = new JSONArray();
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", "You are an English grammar expert. Analyze the grammar of the following sentence and explain it in Chinese.");
        messages.add(systemMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", text);
        messages.add(userMsg);

        return callAiApi(messages);
    }

    @Override
    public String generateGreeting(String role, String topic) {
        JSONArray messages = new JSONArray();
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", "You are a " + role + ". Generate a short, friendly greeting about " + topic + " in English.");
        messages.add(systemMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", "Generate a greeting");
        messages.add(userMsg);

        return callAiApi(messages);
    }

    @Override
    public Map<String, Object> practice(String userMessage, String aiMessage) {
        JSONArray messages = new JSONArray();
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", "You are an English pronunciation and grammar coach. Evaluate the user's response and provide feedback in JSON format with keys: score (0-100), grammar_analysis, pronunciation_tips, suggestion.");
        messages.add(systemMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", "AI said: " + aiMessage + "\nUser responded: " + userMessage);
        messages.add(userMsg);

        String result = callAiApi(messages);
        Map<String, Object> practiceResult = new HashMap<>();
        practiceResult.put("feedback", result);
        return practiceResult;
    }

    private String callAiApi(JSONArray messages) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", model);
        requestBody.put("messages", messages);

        RequestBody body = RequestBody.create(
                requestBody.toJSONString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JSONObject json = JSON.parseObject(responseBody);
                JSONArray choices = json.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    return choices.getJSONObject(0).getJSONObject("message").getString("content");
                }
            }
            log.error("AI API call failed: {}", response.code());
            return "AI service is temporarily unavailable.";
        } catch (IOException e) {
            log.error("AI API call error: {}", e.getMessage());
            return "AI service error: " + e.getMessage();
        }
    }
}
