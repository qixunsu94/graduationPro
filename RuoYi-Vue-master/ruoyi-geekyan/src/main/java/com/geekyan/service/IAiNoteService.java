package com.geekyan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.geekyan.entity.AiNote;

import java.util.List;
import java.util.Map;

public interface IAiNoteService extends IService<AiNote> {

        AiNote autoExtractFromChat(Long userId, String sessionId, String groupId, String subject,
                        String questionText, String aiContent, Map<String, Object> hiddenJson, String imageUrl);

        AiNote autoExtractFromChat(Long userId, String sessionId, String groupId, String subject,
                        String questionText, String aiContent, Map<String, Object> hiddenJson, String imageUrl, Long articleId);

        AiNote autoExtractFromChat(Long userId, String sessionId, String groupId, String subject,
                        String questionText, String aiContent, String hiddenJson, String imageUrl);

        AiNote autoExtractFromChat(Long userId, String sessionId, String groupId, String subject,
                        String questionText, String aiContent, String hiddenJson, String imageUrl, Long articleId);

        AiNote autoExtractFromChat(Long userId, String sessionId, String groupId, String subject,
                        String questionText, String aiContent, String hiddenJson);

        AiNote updateByGroupId(Long userId, String groupId, String questionText, String aiContent, String imageUrl);

        AiNote updateByGroupId(Long userId, String groupId, String questionText, String aiContent);

        void deleteByGroupId(Long userId, String groupId);

        List<AiNote> getUserNotes(Long userId, String subject);

        Map<String, Object> getDailySummary(Long userId);
}
