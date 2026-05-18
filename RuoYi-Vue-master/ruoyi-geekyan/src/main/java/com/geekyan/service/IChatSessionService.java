package com.geekyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.geekyan.entity.ChatSession;
import java.util.Map;

public interface IChatSessionService extends IService<ChatSession> {
    ChatSession getOrCreateDefault(Long userId);
    Map<String, Object> getSessionDetail(String sessionId, Long userId);
    String generateGreeting(String sessionId, Long userId);
}
