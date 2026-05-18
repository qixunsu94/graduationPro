package com.geekyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.ChatMessage;
import com.geekyan.mapper.ChatMessageMapper;
import com.geekyan.service.IChatMessageService;
import com.geekyan.service.IAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements IChatMessageService {

    @Autowired
    private IAiService aiService;

    @Override
    public Map<String, Object> sendMessage(Long userId, String sessionId, String message, String fileName) {
        ChatMessage userMsg = new ChatMessage();
        userMsg.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        userMsg.setSessionId(sessionId);
        userMsg.setUserId(userId);
        userMsg.setContent(message);
        userMsg.setRole("USER");
        userMsg.setFileName(fileName);
        save(userMsg);

        String aiReply = aiService.chat("You are an English learning assistant.", message, sessionId);

        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        aiMsg.setSessionId(sessionId);
        aiMsg.setUserId(userId);
        aiMsg.setContent(aiReply);
        aiMsg.setRole("ASSISTANT");
        aiMsg.setSendMessageId(userMsg.getMessageId());
        save(aiMsg);

        Map<String, Object> result = new HashMap<>();
        result.put("message_id", aiMsg.getMessageId());
        result.put("content", aiReply);
        result.put("role", "ASSISTANT");
        return result;
    }

    @Override
    public Map<String, Object> translateMessage(String messageId) {
        ChatMessage msg = getOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getMessageId, messageId));
        Map<String, Object> result = new HashMap<>();
        if (msg != null) {
            String translation = aiService.translate(msg.getContent(), "English", "Chinese");
            msg.setTranslation(translation);
            updateById(msg);
            result.put("translation", translation);
        }
        return result;
    }

    @Override
    public Map<String, Object> practiceMessage(String messageId) {
        ChatMessage msg = getOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getMessageId, messageId));
        Map<String, Object> result = new HashMap<>();
        if (msg != null) {
            Map<String, Object> practiceResult = aiService.practice("", msg.getContent());
            result.putAll(practiceResult);
        }
        return result;
    }

    @Override
    public void deleteLatestMessage(String sessionId) {
        ChatMessage latest = getOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getCreateTime)
                .last("LIMIT 1"));
        if (latest != null) {
            removeById(latest.getId());
        }
    }

    @Override
    public void deleteAllMessages(String sessionId) {
        remove(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
    }
}
