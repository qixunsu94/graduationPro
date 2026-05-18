package com.geekyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.geekyan.entity.ChatMessage;
import java.util.Map;

public interface IChatMessageService extends IService<ChatMessage> {
    Map<String, Object> sendMessage(Long userId, String sessionId, String message, String fileName);
    Map<String, Object> translateMessage(String messageId);
    Map<String, Object> practiceMessage(String messageId);
    void deleteLatestMessage(String sessionId);
    void deleteAllMessages(String sessionId);
}
