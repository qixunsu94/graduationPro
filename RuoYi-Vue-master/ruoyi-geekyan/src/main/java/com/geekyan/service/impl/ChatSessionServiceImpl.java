package com.geekyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.ChatSession;
import com.geekyan.mapper.ChatSessionMapper;
import com.geekyan.service.IChatSessionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements IChatSessionService {

    @Override
    public ChatSession getOrCreateDefault(Long userId) {
        ChatSession session = getOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getCreateTime)
                .last("LIMIT 1"));
        if (session == null) {
            session = new ChatSession();
            session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
            session.setUserId(userId);
            session.setName("日常对话");
            session.setRole("通用AI助手");
            session.setTopic("日常对话");
            session.setSessionType("TOPIC");
            session.setSubject("general");
            session.setMessageCount(0);
            session.setCreateTime(LocalDateTime.now());
            session.setUpdateTime(LocalDateTime.now());
            save(session);
        }
        return session;
    }

    @Override
    public Map<String, Object> getSessionDetail(String sessionId, Long userId) {
        ChatSession session = getOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, userId));
        Map<String, Object> result = new HashMap<>();
        if (session != null) {
            result.put("session", session);
        }
        return result;
    }

    @Override
    public String generateGreeting(String sessionId, Long userId) {
        return "Hello! I'm your AI learning assistant. How can I help you today?";
    }
}
