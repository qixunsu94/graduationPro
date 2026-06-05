package com.geekyan.controller;

import com.alibaba.fastjson2.JSON;
import com.geekyan.entity.AiNote;
import com.geekyan.entity.ChatSession;
import com.geekyan.entity.ChatMessage;
import com.geekyan.service.IChatSessionService;
import com.geekyan.service.IChatMessageService;
import com.geekyan.service.IAiService;
import com.geekyan.service.IAiNoteService;
import com.geekyan.service.ILearningRecordService;
import com.geekyan.service.IPracticeService;
import com.geekyan.util.AiTextCleaner;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/geekyan/chat")
public class ChatController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private IChatSessionService chatSessionService;

    @Autowired
    private IChatMessageService chatMessageService;

    @Autowired
    private IAiService aiService;

    @Autowired
    private IAiNoteService aiNoteService;

    @Autowired
    private ILearningRecordService learningRecordService;

    @Autowired
    private IPracticeService practiceService;

    @GetMapping("/sessions/grouped")
    public AjaxResult sessionListGrouped(@RequestParam(required = false) String subject,
            @RequestParam(required = false) String sessionType) {
        Long userId = getUserId();
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId);
        if (subject != null && !subject.trim().isEmpty()) {
            wrapper.eq(ChatSession::getSubject, subject.trim());
        }
        if (sessionType != null && !sessionType.trim().isEmpty() && !"ALL".equalsIgnoreCase(sessionType.trim())) {
            wrapper.eq(ChatSession::getSessionType, sessionType.trim().toUpperCase());
        } else {
            wrapper.eq(ChatSession::getSessionType, "TOPIC");
        }
        wrapper.orderByDesc(ChatSession::getCreateTime);
        // 过滤掉 quick-% 临时会话（英语查询辅助产生的脏数据）
        wrapper.notLike(ChatSession::getSessionId, "quick-");
        List<ChatSession> allSessions = chatSessionService.list(wrapper);

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (ChatSession session : allSessions) {
            String sessionSubject = session.getSubject() != null ? session.getSubject() : "general";
            String label = SUBJECT_LABELS.getOrDefault(sessionSubject, sessionSubject);
            if (!grouped.containsKey(sessionSubject)) {
                grouped.put(sessionSubject, new ArrayList<>());
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", session.getId());
            item.put("sessionId", session.getSessionId());
            item.put("name", session.getName());
            item.put("subject", sessionSubject);
            item.put("subjectLabel", label);
            item.put("sessionType", session.getSessionType());
            item.put("messageCount", session.getMessageCount());
            item.put("lastMessageTime", session.getLastMessageTime());
            item.put("createTime", session.getCreateTime());
            grouped.get(sessionSubject).add(item);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            Map<String, Object> group = new HashMap<>();
            group.put("subject", entry.getKey());
            group.put("subjectLabel", SUBJECT_LABELS.getOrDefault(entry.getKey(), entry.getKey()));
            group.put("sessions", entry.getValue());
            group.put("count", entry.getValue().size());
            result.add(group);
        }

        return success(result);
    }

    private static final Map<String, String> SUBJECT_ROLES = new LinkedHashMap<>();
    private static final Set<String> VALID_SUBJECTS = new HashSet<>();
    static {
        SUBJECT_ROLES.put("english", "英语私教");
        SUBJECT_ROLES.put("math", "高数辅导专家");
        SUBJECT_ROLES.put("ds", "数据结构辅导专家");
        SUBJECT_ROLES.put("os", "操作系统辅导专家");
        SUBJECT_ROLES.put("co", "计算机组成原理辅导专家");
        SUBJECT_ROLES.put("cn", "计算机网络辅导专家");
        SUBJECT_ROLES.put("408exam", "408考研辅导专家");
        SUBJECT_ROLES.put("reading", "精读辅导专家");
        SUBJECT_ROLES.put("general", "通用AI助手");
        VALID_SUBJECTS.addAll(SUBJECT_ROLES.keySet());
    }

    private static final Map<String, String> SUBJECT_LABELS = new LinkedHashMap<>();
    static {
        SUBJECT_LABELS.put("english", "英语");
        SUBJECT_LABELS.put("math", "高数");
        SUBJECT_LABELS.put("ds", "数据结构");
        SUBJECT_LABELS.put("os", "操作系统");
        SUBJECT_LABELS.put("co", "计算机组成原理");
        SUBJECT_LABELS.put("cn", "计算机网络");
        SUBJECT_LABELS.put("408exam", "408考研");
        SUBJECT_LABELS.put("reading", "精读");
        SUBJECT_LABELS.put("general", "通用");
    }

    private String resolveSubject(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String subject = ((String) value).trim();
        return VALID_SUBJECTS.contains(subject) ? subject : null;
    }

    /**
     * 获取用户的默认会话（最近使用的一个），如果没有则自动创建
     */
    @GetMapping("/sessions/default")
    public AjaxResult getDefaultSession() {
        Long userId = getUserId();
        ChatSession session = chatSessionService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getSessionType, "TOPIC")
                        .orderByDesc(ChatSession::getLastMessageTime)
                        .last("LIMIT 1"));

        if (session == null) {
            // 没有任何会话，自动创建一个默认的
            session = new ChatSession();
            session.setUserId(userId);
            session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
            session.setSubject("general");
            session.setName("日常对话");
            session.setRole("通用AI助手");
            session.setSessionType("TOPIC");
            session.setMessageCount(0);
            session.setCreateTime(LocalDateTime.now());
            session.setUpdateTime(LocalDateTime.now());
            chatSessionService.save(session);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", session.getId());
        result.put("sessionId", session.getSessionId());
        result.put("name", session.getName());
        result.put("subject", session.getSubject());
        result.put("sessionType", session.getSessionType());
        return success(result);
    }

    @PostMapping("/sessions")
    public AjaxResult createSession(@RequestBody ChatSession session) {
        session.setUserId(getUserId());
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        if (session.getSubject() == null || session.getSubject().isEmpty()) {
            session.setSubject("general");
        }
        if (!VALID_SUBJECTS.contains(session.getSubject())) {
            return error("不支持的学科类型: " + session.getSubject() + "，可选值: " + VALID_SUBJECTS);
        }
        if (session.getRole() == null || session.getRole().isEmpty()) {
            session.setRole(SUBJECT_ROLES.getOrDefault(session.getSubject(), "通用AI助手"));
        }
        if (session.getSessionType() == null || session.getSessionType().isEmpty()) {
            session.setSessionType("TOPIC");
        } else {
            session.setSessionType(session.getSessionType().trim().toUpperCase());
        }
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        chatSessionService.save(session);
        return success(session);
    }

    @GetMapping("/sessions")
    public AjaxResult sessionList(@RequestParam(required = false) String subject,
            @RequestParam(required = false) String sessionType) {
        Long userId = getUserId();
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId);
        if (subject != null && !subject.trim().isEmpty()) {
            wrapper.eq(ChatSession::getSubject, subject.trim());
        }
        if (sessionType != null && !sessionType.trim().isEmpty() && !"ALL".equalsIgnoreCase(sessionType.trim())) {
            wrapper.eq(ChatSession::getSessionType, sessionType.trim().toUpperCase());
        } else {
            wrapper.eq(ChatSession::getSessionType, "TOPIC");
        }
        wrapper.orderByDesc(ChatSession::getLastMessageTime).orderByDesc(ChatSession::getCreateTime);
        // 过滤掉 quick-% 临时会话
        wrapper.notLike(ChatSession::getSessionId, "quick-");
        return success(chatSessionService.list(wrapper));
    }

    @GetMapping("/sessions/{sessionId}")
    public AjaxResult getSessionDetail(@PathVariable String sessionId) {
        Map<String, Object> detail = chatSessionService.getSessionDetail(sessionId, getUserId());
        return success(detail);
    }

    @GetMapping("/sessions/{sessionId}/greeting")
    public AjaxResult getSessionGreeting(@PathVariable String sessionId) {
        return success("你好，我是AI学习助手，有什么题目需要我帮你分析吗？");
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public AjaxResult getSessionMessages(@PathVariable String sessionId) {
        Long userId = getUserId();
        List<ChatMessage> list = chatMessageService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getUserId, userId)
                        .orderByAsc(ChatMessage::getCreateTime));
        return success(list);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public AjaxResult deleteSession(@PathVariable String sessionId) {
        return toAjax(chatSessionService.remove(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getUserId, getUserId())));
    }

    @DeleteMapping("/sessions/batch")
    public AjaxResult batchDeleteSessions(@RequestBody Map<String, Object> params) {
        Object sessionIdsObj = params.get("sessionIds");
        if (sessionIdsObj == null) {
            return error("sessionIds不能为空");
        }

        List<String> sessionIds;
        if (sessionIdsObj instanceof List) {
            sessionIds = (List<String>) sessionIdsObj;
        } else {
            return error("sessionIds格式错误，应为字符串数组");
        }

        if (sessionIds.isEmpty()) {
            return error("sessionIds不能为空数组");
        }

        Long userId = getUserId();
        int deleted = 0;
        for (String sessionId : sessionIds) {
            boolean removed = chatSessionService.remove(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                            .eq(ChatSession::getSessionId, sessionId)
                            .eq(ChatSession::getUserId, userId));
            if (removed) {
                chatMessageService.remove(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getSessionId, sessionId)
                                .eq(ChatMessage::getUserId, userId));
                deleted++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("deleted", deleted);
        result.put("total", sessionIds.size());
        return success(result);
    }

    @PostMapping("/sessions/{sessionId}/chat")
    public AjaxResult sessionChat(@PathVariable String sessionId, @RequestBody Map<String, Object> params) {
        String message = (String) params.get("message");
        String imageBase64 = (String) params.get("image_base64");
        String requestedSubject = resolveSubject(params.get("mode"));
        Boolean newQuestion = params.get("new_question") != null && Boolean.TRUE.equals(params.get("new_question"));
        Boolean skipExtract = params.get("skip_extract") != null && Boolean.TRUE.equals(params.get("skip_extract"));

        Long userId = getUserId();

        ChatSession session = chatSessionService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getUserId, userId));

        boolean practiceSessionRequest = sessionId.startsWith("practice-")
                || (session != null && "PRACTICE".equalsIgnoreCase(session.getSessionType()));
        if (practiceSessionRequest) {
            if (session == null) {
                return error("未找到对练会话");
            }
            return success(practiceService.answerPractice(sessionId, message, imageBase64));
        }

        if (session == null) {
            String subject = requestedSubject != null ? requestedSubject : "general";
            session = new ChatSession();
            session.setSessionId(sessionId);
            session.setUserId(userId);
            session.setName(SUBJECT_LABELS.getOrDefault(subject, "日常对话"));
            session.setRole(SUBJECT_ROLES.getOrDefault(subject, "通用AI助手"));
            session.setTopic(SUBJECT_LABELS.getOrDefault(subject, "日常对话"));
            session.setSessionType("TOPIC");
            session.setSubject(subject);
            session.setMessageCount(0);
            session.setCreateTime(LocalDateTime.now());
            session.setUpdateTime(LocalDateTime.now());
            chatSessionService.save(session);
        }

        String subject = session.getSubject() != null ? session.getSubject() : "general";
        boolean isPractice = "PRACTICE".equalsIgnoreCase(session.getSessionType()) || sessionId.startsWith("practice-");
        // 自动更新会话名称：如果name是默认值，用第一条消息的前几个字
        if (message != null && !message.trim().isEmpty()) {
            String currentName = session.getName();
            boolean isDefaultName = currentName == null || currentName.isEmpty()
                    || "新会话".equals(currentName) || SUBJECT_LABELS.containsValue(currentName);
            if (isDefaultName) {
                String autoName = message.trim().replaceAll("[\\r\\n]+", " ");
                if (autoName.length() > 8)
                    autoName = autoName.substring(0, 8) + "…";
                session.setName(autoName);
                session.setUpdateTime(LocalDateTime.now());
                chatSessionService.updateById(session);
            }
        }
        boolean subjectChanged = false;
        if (isPractice) {
            skipExtract = true;
        } else if (requestedSubject != null && !requestedSubject.equals(subject)) {
            subject = requestedSubject;
            subjectChanged = true;
            session.setSubject(subject);
            session.setRole(SUBJECT_ROLES.getOrDefault(subject, "通用AI助手"));
            session.setUpdateTime(LocalDateTime.now());
            chatSessionService.updateById(session);
        } else if (requestedSubject == null && "english".equals(subject)) {
            // 自动检测：如果当前是英语模式但消息明显是其他学科，自动切换
            String detectedSubject = detectSubjectFromMessage(message);
            if (detectedSubject != null && !detectedSubject.equals(subject)) {
                subject = detectedSubject;
                subjectChanged = true;
                session.setSubject(subject);
                session.setRole(SUBJECT_ROLES.getOrDefault(subject, "通用AI助手"));
                session.setUpdateTime(LocalDateTime.now());
                chatSessionService.updateById(session);
            }
        }

        String groupId;
        if (newQuestion || subjectChanged) {
            // 新问题或科目切换时，结束旧分组并创建新分组
            if (session.getGroupId() != null && session.getGroupEnded() != null && session.getGroupEnded() == 0) {
                session.setGroupEnded(1);
                session.setUpdateTime(LocalDateTime.now());
                chatSessionService.updateById(session);
            }
            groupId = "grp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            session.setGroupId(groupId);
            session.setGroupEnded(0);
            session.setUpdateTime(LocalDateTime.now());
            chatSessionService.updateById(session);
        } else if (session.getGroupId() != null && session.getGroupEnded() != null && session.getGroupEnded() == 0) {
            groupId = session.getGroupId();
        } else {
            groupId = "grp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            session.setGroupId(groupId);
            session.setGroupEnded(0);
            session.setUpdateTime(LocalDateTime.now());
            chatSessionService.updateById(session);
        }

        String aiReply;
        if (imageBase64 != null && !imageBase64.isEmpty()) {
            aiReply = aiService.chatWithImage(subject, message, imageBase64, sessionId);
        } else {
            aiReply = aiService.chatWithMode(subject, message, sessionId);
        }

        if (aiReply == null || aiReply.isEmpty()) {
            return error("AI 服务暂时不可用，请稍后重试");
        }

        String hiddenJsonStr = extractHiddenJson(aiReply);
        String displayContent = cleanChatDisplayText(stripHiddenJson(aiReply));
        List<Map<String, String>> sections = parseSections(displayContent);

        // 立即解析hiddenJson为Map，供下游使用
        Map<String, Object> hiddenJsonMap = null;
        if (hiddenJsonStr != null && !hiddenJsonStr.isEmpty()) {
            try {
                hiddenJsonMap = JSON.parseObject(hiddenJsonStr,
                        new com.alibaba.fastjson2.TypeReference<Map<String, Object>>() {
                        });
            } catch (Exception e) {
                log.warn("解析hiddenJson失败: {}", hiddenJsonStr, e);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("message_id", UUID.randomUUID().toString().replace("-", ""));
        result.put("content", displayContent);
        result.put("role", "ASSISTANT");
        result.put("reply", displayContent);
        result.put("group_id", groupId);
        result.put("sessionId", session.getSessionId());
        result.put("sections", sections);
        result.put("subject", subject);

        try {
            ChatMessage userMsg = new ChatMessage();
            userMsg.setMessageId(UUID.randomUUID().toString().replace("-", ""));
            userMsg.setSessionId(sessionId);
            userMsg.setGroupId(groupId);
            userMsg.setUserId(userId);
            userMsg.setContent(message);
            userMsg.setRole("USER");
            userMsg.setSubject(session.getSubject());
            if (imageBase64 != null && !imageBase64.isEmpty()) {
                userMsg.setImageUrl("base64_upload");
            }
            userMsg.setCreateTime(LocalDateTime.now());
            userMsg.setUpdateTime(LocalDateTime.now());
            chatMessageService.save(userMsg);

            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setMessageId(UUID.randomUUID().toString().replace("-", ""));
            aiMsg.setSessionId(sessionId);
            aiMsg.setGroupId(groupId);
            aiMsg.setUserId(userId);
            aiMsg.setContent(displayContent);
            aiMsg.setRole("ASSISTANT");
            aiMsg.setSendMessageId(userMsg.getMessageId());
            aiMsg.setSubject(session.getSubject());
            if (!sections.isEmpty()) {
                aiMsg.setSections(JSON.toJSONString(sections));
            }
            if (hiddenJsonStr != null && !hiddenJsonStr.isEmpty()) {
                aiMsg.setHiddenJson(hiddenJsonStr);
            }
            aiMsg.setCreateTime(LocalDateTime.now());
            aiMsg.setUpdateTime(LocalDateTime.now());
            chatMessageService.save(aiMsg);

            result.put("message_id", aiMsg.getMessageId());

            final Long asyncUserId = userId;
            final String asyncSessionId = sessionId;
            final String asyncGroupId = groupId;
            final String asyncSubject = subject;
            final String asyncMessage = message;
            final String asyncDisplayContent = displayContent;
            final Map<String, Object> asyncHiddenJsonMap = hiddenJsonMap;
            final String asyncImageUrl = (imageBase64 != null && !imageBase64.isEmpty()) ? "base64_upload" : null;
            final boolean asyncSkipExtract = skipExtract;
            CompletableFuture.runAsync(() -> {
                try {
                    if (!asyncSkipExtract) {
                        log.info("开始自动提取笔记: userId={}, sessionId={}, groupId={}, subject={}, messageLength={}",
                                asyncUserId, asyncSessionId, asyncGroupId, asyncSubject,
                                asyncMessage != null ? asyncMessage.length() : 0);
                        AiNote savedNote = aiNoteService.autoExtractFromChat(asyncUserId, asyncSessionId, asyncGroupId,
                                asyncSubject, asyncMessage, asyncDisplayContent, asyncHiddenJsonMap, asyncImageUrl);
                        if (savedNote != null) {
                            log.info("自动提取笔记成功: noteId={}, title={}", savedNote.getId(), savedNote.getTitle());
                        } else {
                            log.warn("自动提取笔记返回null: userId={}, subject={}, message={}", asyncUserId, asyncSubject,
                                    asyncMessage);
                        }
                    } else {
                        log.info("skip_extract=true，跳过自动提取笔记: userId={}, sessionId={}", asyncUserId,
                                asyncSessionId);
                    }
                } catch (Exception e) {
                    log.error("自动提取笔记失败: userId={}, subject={}, error={}", asyncUserId, asyncSubject, e.getMessage(),
                            e);
                }
            });
        } catch (Exception e) {
            log.warn("保存聊天记录失败，sessionId={}: {}", sessionId, e.getMessage());
        }

        try {
            String recordType = "PRACTICE".equalsIgnoreCase(session.getSessionType()) ? "practice" : "chat";
            learningRecordService.recordLearning(userId, recordType, null, message, null,
                    subject, recordType, sessionId);
        } catch (Exception e) {
            log.warn("记录学习行为失败: {}", e.getMessage());
        }

        return success(result);
    }

    /**
     * 一次性AI查询接口：不保存聊天历史，不创建会话，不提取笔记
     * 适用于精读、单词详情、翻译等非聊天场景的AI调用，避免污染通用对话模块
     */

    @PostMapping("/sessions/{sessionId}/edit-message")
    public AjaxResult editMessage(@PathVariable String sessionId, @RequestBody Map<String, Object> params) {
        String messageId = (String) params.get("message_id");
        String newContent = (String) params.get("content");
        String newImageBase64 = (String) params.get("image_base64");
        Long userId = getUserId();

        ChatSession session = chatSessionService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getUserId, userId));

        if (session == null) {
            return error("会话不存在");
        }

        String subject = session.getSubject() != null ? session.getSubject() : "general";

        ChatMessage targetMsg = chatMessageService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getMessageId, messageId)
                        .eq(ChatMessage::getUserId, userId));

        if (targetMsg == null) {
            return error("消息不存在");
        }

        String groupId = targetMsg.getGroupId();

        chatMessageService.remove(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getUserId, userId)
                        .eq(ChatMessage::getGroupId, groupId)
                        .ge(ChatMessage::getCreateTime, targetMsg.getCreateTime()));

        String aiReply;
        if (newImageBase64 != null && !newImageBase64.isEmpty()) {
            aiReply = aiService.chatWithImage(subject, newContent, newImageBase64, sessionId);
        } else {
            aiReply = aiService.chatWithMode(subject, newContent, sessionId);
        }
        if (aiReply == null || aiReply.isEmpty()) {
            return error("AI 服务暂时不可用");
        }

        String editHiddenJson = extractHiddenJson(aiReply);
        String editDisplayContent = stripHiddenJson(aiReply);
        List<Map<String, String>> sections = parseSections(aiReply);

        ChatMessage newUserMsg = new ChatMessage();
        newUserMsg.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        newUserMsg.setSessionId(sessionId);
        newUserMsg.setGroupId(groupId);
        newUserMsg.setUserId(userId);
        newUserMsg.setContent(newContent);
        newUserMsg.setRole("USER");
        newUserMsg.setSubject(subject);
        if (newImageBase64 != null && !newImageBase64.isEmpty()) {
            newUserMsg.setImageUrl("base64_upload");
        }
        newUserMsg.setCreateTime(LocalDateTime.now());
        newUserMsg.setUpdateTime(LocalDateTime.now());
        chatMessageService.save(newUserMsg);

        ChatMessage newAiMsg = new ChatMessage();
        newAiMsg.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        newAiMsg.setSessionId(sessionId);
        newAiMsg.setGroupId(groupId);
        newAiMsg.setUserId(userId);
        newAiMsg.setContent(editDisplayContent);
        newAiMsg.setRole("ASSISTANT");
        newAiMsg.setSendMessageId(newUserMsg.getMessageId());
        newAiMsg.setSubject(subject);
        if (!sections.isEmpty()) {
            newAiMsg.setSections(JSON.toJSONString(sections));
        }
        if (editHiddenJson != null && !editHiddenJson.isEmpty()) {
            newAiMsg.setHiddenJson(editHiddenJson);
        }
        newAiMsg.setCreateTime(LocalDateTime.now());
        newAiMsg.setUpdateTime(LocalDateTime.now());
        chatMessageService.save(newAiMsg);

        try {
            String imageUrl = (newImageBase64 != null && !newImageBase64.isEmpty()) ? "base64_upload" : null;
            if (!"PRACTICE".equalsIgnoreCase(session.getSessionType())) {
                aiNoteService.updateByGroupId(userId, groupId, newContent, editDisplayContent, imageUrl);
            }
        } catch (Exception e) {
            log.warn("更新笔记失败: {}", e.getMessage());
        }

        try {
            String recordType = "PRACTICE".equalsIgnoreCase(session.getSessionType()) ? "practice" : "chat";
            learningRecordService.recordLearning(userId, recordType, null, newContent, null,
                    subject, recordType, sessionId);
        } catch (Exception e) {
            log.warn("记录学习行为失败: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("user_message_id", newUserMsg.getMessageId());
        result.put("ai_message_id", newAiMsg.getMessageId());
        result.put("content", editDisplayContent);
        result.put("sections", sections);
        result.put("group_id", groupId);
        return success(result);
    }

    @PostMapping("/sessions/{sessionId}/delete-group")
    public AjaxResult deleteGroup(@PathVariable String sessionId, @RequestBody Map<String, String> params) {
        String groupId = params.get("group_id");
        Long userId = getUserId();

        chatMessageService.remove(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getUserId, userId)
                        .eq(ChatMessage::getGroupId, groupId));

        aiNoteService.deleteByGroupId(userId, groupId);
        return success();
    }

    @GetMapping("/notes")
    public TableDataInfo noteList(@RequestParam(required = false) String subject) {
        startPage();
        List<AiNote> list = aiNoteService.getUserNotes(getUserId(), subject);
        return getDataTable(list);
    }

    @GetMapping("/notes/{id}")
    public AjaxResult noteDetail(@PathVariable Long id) {
        AiNote note = aiNoteService.getById(id);
        if (note == null || !note.getUserId().equals(getUserId())) {
            return error("笔记不存在");
        }
        return success(note);
    }

    @PutMapping("/notes/{id}")
    public AjaxResult updateNote(@PathVariable Long id, @RequestBody AiNote noteUpdate) {
        AiNote existing = aiNoteService.getById(id);
        if (existing == null || !existing.getUserId().equals(getUserId())) {
            return error("笔记不存在");
        }
        if (noteUpdate.getUserNotes() != null) {
            existing.setUserNotes(noteUpdate.getUserNotes());
        }
        if (noteUpdate.getKnowledgeTags() != null) {
            existing.setKnowledgeTags(noteUpdate.getKnowledgeTags());
        }
        if (noteUpdate.getQuestionText() != null) {
            existing.setQuestionText(noteUpdate.getQuestionText());
        }
        if (noteUpdate.getKeyPoints() != null) {
            existing.setKeyPoints(AiTextCleaner.clean(noteUpdate.getKeyPoints()));
        }
        if (noteUpdate.getAiContent() != null) {
            existing.setAiContent(AiTextCleaner.clean(noteUpdate.getAiContent()));
        }
        if (noteUpdate.getTitle() != null) {
            existing.setTitle(noteUpdate.getTitle());
        }
        return toAjax(aiNoteService.updateById(existing));
    }

    @DeleteMapping("/notes/{ids}")
    public AjaxResult deleteNotes(@PathVariable Long[] ids) {
        return toAjax(aiNoteService.removeByIds(Arrays.asList(ids)));
    }

    @GetMapping("/daily-summary")
    public AjaxResult dailySummary() {
        Map<String, Object> summary = aiNoteService.getDailySummary(getUserId());
        return success(summary);
    }

    @PostMapping("/translate/{messageId}")
    public AjaxResult translateMessage(@PathVariable String messageId) {
        return success(chatMessageService.translateMessage(messageId));
    }

    @PostMapping("/practice/{messageId}")
    public AjaxResult practiceMessage(@PathVariable String messageId) {
        return success(chatMessageService.practiceMessage(messageId));
    }

    private List<Map<String, String>> parseSections(String aiReply) {
        List<Map<String, String>> sections = new ArrayList<>();

        String contentWithoutJson = aiReply;
        Pattern jsonBlockPattern = Pattern.compile("```json\\s*\\n([\\s\\S]*?)\\n\\s*```");
        Matcher jsonMatcher = jsonBlockPattern.matcher(aiReply);
        if (jsonMatcher.find()) {
            contentWithoutJson = aiReply.substring(0, jsonMatcher.start()).trim();
        }

        Pattern pattern = Pattern.compile("第\\d+段[：:]\\s*(.+)");
        Matcher matcher = pattern.matcher(contentWithoutJson);

        List<int[]> ranges = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            ranges.add(new int[] { matcher.start(), matcher.end() });
            titles.add(matcher.group(1).trim());
        }

        if (ranges.isEmpty()) {
            Pattern oldPattern = Pattern.compile("【([^】]+)】");
            Matcher oldMatcher = oldPattern.matcher(contentWithoutJson);
            while (oldMatcher.find()) {
                ranges.add(new int[] { oldMatcher.start(), oldMatcher.end() });
                titles.add(oldMatcher.group(1).trim());
            }
        }

        if (ranges.isEmpty()) {
            return sections;
        }

        for (int i = 0; i < ranges.size(); i++) {
            int contentStart = ranges.get(i)[1];
            int contentEnd = (i + 1 < ranges.size()) ? ranges.get(i + 1)[0] : contentWithoutJson.length();
            String content = contentWithoutJson.substring(contentStart, contentEnd).trim();
            Map<String, String> section = new HashMap<>();
            section.put("title", titles.get(i));
            section.put("content", content);
            sections.add(section);
        }

        return sections;
    }

    private String extractHiddenJson(String aiReply) {
        if (aiReply == null)
            return null;
        Pattern jsonBlockPattern = Pattern.compile("```json\\s*\\n([\\s\\S]*?)\\n\\s*```");
        Matcher matcher = jsonBlockPattern.matcher(aiReply);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        int lastBrace = aiReply.lastIndexOf('}');
        if (lastBrace > 0) {
            int searchStart = Math.max(0, lastBrace - 2000);
            String tail = aiReply.substring(searchStart);
            int braceStart = -1;
            int depth = 0;
            for (int i = tail.length() - 1; i >= 0; i--) {
                char c = tail.charAt(i);
                if (c == '}')
                    depth++;
                else if (c == '{') {
                    depth--;
                    if (depth == 0) {
                        braceStart = i;
                        break;
                    }
                }
            }
            if (braceStart >= 0) {
                String candidate = tail.substring(braceStart).trim();
                try {
                    JSON.parseObject(candidate);
                    return candidate;
                } catch (Exception e) {
                    // not valid JSON
                }
            }
        }
        return null;
    }

    private String stripHiddenJson(String aiReply) {
        if (aiReply == null)
            return null;
        String stripped = aiReply;
        Pattern jsonBlockPattern = Pattern.compile("```json\\s*\\n[\\s\\S]*?\\n\\s*```");
        Matcher matcher = jsonBlockPattern.matcher(stripped);
        if (matcher.find()) {
            stripped = matcher.replaceAll("").trim();
        } else {
            int lastBrace = stripped.lastIndexOf('}');
            if (lastBrace > 0) {
                int searchStart = Math.max(0, lastBrace - 2000);
                String tail = stripped.substring(searchStart);
                int braceStart = -1;
                int depth = 0;
                for (int i = tail.length() - 1; i >= 0; i--) {
                    char c = tail.charAt(i);
                    if (c == '}')
                        depth++;
                    else if (c == '{') {
                        depth--;
                        if (depth == 0) {
                            braceStart = i;
                            break;
                        }
                    }
                }
                if (braceStart >= 0) {
                    String candidate = tail.substring(braceStart).trim();
                    try {
                        JSON.parseObject(candidate);
                        String before = stripped.substring(0, searchStart + braceStart).trim();
                        String after = stripped.substring(searchStart + lastBrace - searchStart + 1).trim();
                        if (after.isEmpty()) {
                            stripped = before;
                        } else {
                            stripped = before + "\n" + after;
                        }
                    } catch (Exception e) {
                        // not valid JSON at end, keep as is
                    }
                }
            }
        }
        return stripped.trim();
    }

    private String cleanChatDisplayText(String value) {
        String cleaned = AiTextCleaner.clean(value);
        if (cleaned == null) {
            return null;
        }
        return cleaned
                .replaceAll("\\$([^$\\n]{1,240})\\$", "$1")
                .replace("\\(", "")
                .replace("\\)", "")
                .replace("\\[", "")
                .replace("\\]", "")
                .replaceAll("\\\\frac\\{([^{}]+)\\}\\{([^{}]+)\\}", "($1)/($2)")
                .replaceAll("\\\\sum\\s*", "sum ")
                .replaceAll("\\\\int\\s*", "int ")
                .replaceAll("\\\\lim\\s*", "lim ")
                .replace("\\", "")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    @PostMapping("/ocr")
    public AjaxResult ocrRecognize(@RequestBody Map<String, String> params) {
        String imageBase64 = params.get("image_base64");
        if (imageBase64 == null || imageBase64.isEmpty()) {
            return error("图片数据不能为空");
        }

        String ocrPrompt = "你是一个专业的题目识别和规范化助手。请完成以下任务：\n" +
                "1. 识别图片中的所有文字内容，特别是数学公式、符号等\n" +
                "2. 如果图片中包含多个题目，判断哪个是主要题目（通常是最完整的那个），提取该题目的完整文本\n" +
                "3. 去除手写笔记、标记等干扰内容，只保留印刷体题目\n" +
                "4. 对提取的题目文本进行格式化整理，补充可能缺失的标点\n" +
                "5. 数学公式用纯文本表示（如x^2, a_n, 1/n等），不要使用LaTeX\n\n" +
                "直接输出规范化后的题目文本，不要输出其他解释内容。";

        String recognizedText = aiService.chatWithImage("general", ocrPrompt, imageBase64, "ocr-" + getUserId());

        if (recognizedText == null || recognizedText.isEmpty()) {
            return error("图片识别失败，请重试");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("text", recognizedText.trim());
        return success(result);
    }

    /**
     * 一次性AI调用接口 - 不创建会话、不保存消息、不入历史
     * 专供翻译/解析等辅助功能使用，避免污染AI聊天记录
     */
    @PostMapping("/one-shot")
    public AjaxResult oneShot(@RequestBody Map<String, String> params) {
        String systemPrompt = params.get("systemPrompt");
        String userMessage = params.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return error("消息不能为空");
        }
        if (systemPrompt == null || systemPrompt.trim().isEmpty()) {
            systemPrompt = "你是一个专业的英语学习助手。";
        }

        // Cache: 相同的systemPrompt+message直接返回缓存结果（用原始字符串做key避免hashCode冲突）
        String cacheKey = "oneShot:" + safeCacheKey(systemPrompt) + ":" + safeCacheKey(userMessage);
        try {
            String cached = queryCacheService.get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                return AjaxResult.success(JSON.parseObject(cached));
            }
        } catch (Exception e) {
            log.warn("one-shot缓存读取失败: {}", e.getMessage());
        }

        try {
            String aiReply = aiService.chatWithoutHistory(systemPrompt, userMessage);
            if (aiReply == null || aiReply.isEmpty()) {
                return error("AI服务暂不可用，请稍后重试");
            }
            Map<String, Object> result = new HashMap<>();
            result.put("content", aiReply);
            // 解析sections（四段式）
            result.put("sections", parseSections(aiReply));
            // 解析hiddenJson
            String hiddenJson = extractHiddenJson(aiReply);
            if (hiddenJson != null) {
                try {
                    result.put("structured", JSON.parseObject(hiddenJson));
                } catch (Exception e) {
                    log.warn("one-shot结构化JSON解析失败: {}", e.getMessage());
                }
            }

            // 写入缓存，有效期7天
            try {
                queryCacheService.set(cacheKey, JSON.toJSONString(result), 7 * 24 * 3600);
            } catch (Exception e) {
                log.warn("one-shot缓存写入失败: {}", e.getMessage());
            }

            return success(result);
        } catch (Exception e) {
            log.error("one-shot AI调用失败", e);
            return error("AI服务异常：" + e.getMessage());
        }
    }

    /** 安全缓存key：用原始字符串替代hashCode避免冲突，超长截断 */
    private String safeCacheKey(String text) {
        if (text == null)
            return "null";
        String s = text.trim().toLowerCase();
        return s.length() > 120 ? s.substring(0, 120) + ":h" + s.hashCode() : s;
    }

    /** 根据消息内容自动检测学科 */
    private String detectSubjectFromMessage(String message) {
        if (message == null || message.trim().isEmpty())
            return null;
        String q = message.trim();

        // 操作系统关键词
        if (q.matches(
                ".*(银行家算法|死锁|进程调度|页面置换|进程同步|互斥|信号量|管程|虚拟内存|分页|分段|进程|线程|作业调度|磁盘调度|文件系统|操作系统|OS|进程通信|内存管理|缓冲|抖动|饥饿|周转时间).*")) {
            return "os";
        }
        // 数据结构关键词
        if (q.matches(
                ".*(二叉树|红黑树|AVL|B树|B\\+树|哈希|排序算法|图论|最短路径|最小生成树|动态规划|贪心|分治|递归|栈|队列|链表|堆|散列|拓扑排序|KMP|DFS|BFS|数据结构).*")) {
            return "ds";
        }
        // 计算机组成原理关键词
        if (q.matches(".*(总线|指令系统|流水线|Cache|寻址|中断|DMA|浮点|补码|原码|移码|ALU|控制器|存储器|微程序|硬布线|指令流水|数据通路|计组|计算机组成).*")) {
            return "co";
        }
        // 计算机网络关键词
        if (q.matches(
                ".*(TCP|UDP|IP|HTTP|DNS|ARP|路由|子网|掩码|三次握手|四次挥手|滑动窗口|拥塞控制|OSI|MAC|交换机|网关|DHCP|ICMP|计算机网络|计网|协议栈|传输层|网络层|数据链路层|物理层).*")) {
            return "cn";
        }
        // 数学关键词
        if (q.matches(".*(极限|导数|积分|微分|矩阵|行列式|线性代数|概率|统计|微积分|泰勒|拉格朗日|柯西|高斯|向量|特征值|正交|范数|级数|傅里叶|数学|高数).*")) {
            return "math";
        }
        return null;
    }
}
