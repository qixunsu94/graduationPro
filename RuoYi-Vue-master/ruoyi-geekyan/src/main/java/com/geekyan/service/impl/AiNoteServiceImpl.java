package com.geekyan.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.AiNote;
import com.geekyan.entity.ChatMessage;
import com.geekyan.entity.WordBook;
import com.geekyan.entity.LongSentence;
import com.geekyan.mapper.AiNoteMapper;
import com.geekyan.mapper.ChatMessageMapper;
import com.geekyan.mapper.WordBookMapper;
import com.geekyan.mapper.LongSentenceMapper;
import com.geekyan.service.IAiNoteService;
import com.geekyan.service.IAiService;
import com.geekyan.service.IReviewTaskService;
import com.geekyan.service.IWordBookService;
import com.geekyan.service.ILearningRecordService;
import com.geekyan.util.AiTextCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiNoteServiceImpl extends ServiceImpl<AiNoteMapper, AiNote> implements IAiNoteService {

    private static final Logger log = LoggerFactory.getLogger(AiNoteServiceImpl.class);

    @Autowired
    private IAiService aiService;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private IReviewTaskService reviewTaskService;

    @Autowired
    private IWordBookService wordBookService;

    @Autowired
    private WordBookMapper wordBookMapper;

    @Autowired
    private LongSentenceMapper longSentenceMapper;

    @Autowired
    private ILearningRecordService learningRecordService;

    @Autowired
    private com.geekyan.service.IBaiduTranslateService baiduTranslateService;

    @Override
    public AiNote autoExtractFromChat(Long userId, String sessionId, String groupId, String subject,
            String questionText, String aiContent, String hiddenJson) {
        Map<String, Object> hiddenJsonMap = null;
        if (hiddenJson != null && !hiddenJson.isEmpty()) {
            try {
                hiddenJsonMap = JSON.parseObject(hiddenJson,
                        new com.alibaba.fastjson2.TypeReference<Map<String, Object>>() {
                        });
            } catch (Exception e) {
                log.warn("autoExtractFromChat中解析hiddenJson失败: {}", hiddenJson, e);
            }
        }
        return autoExtractFromChat(userId, sessionId, groupId, subject, questionText, aiContent, hiddenJsonMap, null,
                null);
    }

    @Override
    public AiNote autoExtractFromChat(Long userId, String sessionId, String groupId, String subject,
            String questionText, String aiContent, String hiddenJson, String imageUrl) {
        Map<String, Object> hiddenJsonMap = null;
        if (hiddenJson != null && !hiddenJson.isEmpty()) {
            try {
                hiddenJsonMap = JSON.parseObject(hiddenJson,
                        new com.alibaba.fastjson2.TypeReference<Map<String, Object>>() {
                        });
            } catch (Exception e) {
                log.warn("autoExtractFromChat中解析hiddenJson失败: {}", hiddenJson, e);
            }
        }
        return autoExtractFromChat(userId, sessionId, groupId, subject, questionText, aiContent, hiddenJsonMap,
                imageUrl, null);
    }

    @Override
    public AiNote autoExtractFromChat(Long userId, String sessionId, String groupId, String subject,
            String questionText, String aiContent, String hiddenJson, String imageUrl, Long articleId) {
        Map<String, Object> hiddenJsonMap = null;
        if (hiddenJson != null && !hiddenJson.isEmpty()) {
            try {
                hiddenJsonMap = JSON.parseObject(hiddenJson,
                        new com.alibaba.fastjson2.TypeReference<Map<String, Object>>() {
                        });
            } catch (Exception e) {
                log.warn("autoExtractFromChat中解析hiddenJson失败: {}", hiddenJson, e);
            }
        }
        return autoExtractFromChat(userId, sessionId, groupId, subject, questionText, aiContent, hiddenJsonMap,
                imageUrl, articleId);
    }

    @Override
    public AiNote autoExtractFromChat(Long userId, String sessionId, String groupId, String subject,
            String questionText, String aiContent, Map<String, Object> hiddenJson, String imageUrl) {
        return autoExtractFromChat(userId, sessionId, groupId, subject, questionText, aiContent, hiddenJson, imageUrl,
                null);
    }

    @Override
    public AiNote autoExtractFromChat(Long userId, String sessionId, String groupId, String subject,
            String questionText, String aiContent, Map<String, Object> hiddenJson, String imageUrl, Long articleId) {

        log.info(
                "autoExtractFromChat开始: userId={}, sessionId={}, groupId={}, subject={}, questionTextLength={}, aiContentLength={}",
                userId, sessionId, groupId, subject,
                questionText != null ? questionText.length() : 0,
                aiContent != null ? aiContent.length() : 0);
        String cleanedAiContent = AiTextCleaner.clean(aiContent);

        if ("reading".equals(subject)) {
            log.info("精读AI助手对话，走长难句管理+精读笔记路径，不写ai_note: userId={}", userId);
            try {
                autoSaveLongSentence(userId, questionText, cleanedAiContent,
                        hiddenJson != null ? JSON.toJSONString(hiddenJson) : null);
            } catch (Exception e) {
                log.error("精读-自动保存长难句失败: error={}", e.getMessage(), e);
            }
            try {
                autoAddWordToBook(userId, questionText, hiddenJson != null ? JSON.toJSONString(hiddenJson) : null);
            } catch (Exception e) {
                log.error("精读-自动加入生词本失败: error={}", e.getMessage(), e);
            }
            return null;
        }

        if ("english".equals(subject)) {
            String queryType = hiddenJson != null && hiddenJson.get("query_type") != null
                    ? String.valueOf(hiddenJson.get("query_type"))
                    : null;
            log.info("英语查询解析: queryType={}", queryType);

            if ("word".equals(queryType) || "phrase".equals(queryType) || isWordQuery(questionText)) {
                log.info("英语单词/短语查询，添加到生词本: questionText={}", questionText);
                try {
                    autoAddWordToBook(userId, questionText, hiddenJson != null ? JSON.toJSONString(hiddenJson) : null);
                } catch (Exception e) {
                    log.error("自动加入生词本失败: questionText={}, error={}", questionText, e.getMessage(), e);
                }
            }
            if ("sentence".equals(queryType)) {
                log.info("英语长难句查询，核心词汇加入生词本，完整解析写入记录本: questionText={}", questionText);
                try {
                    autoAddWordToBook(userId, questionText, hiddenJson != null ? JSON.toJSONString(hiddenJson) : null);
                } catch (Exception e) {
                    log.error("句子-自动加入生词本失败: error={}", e.getMessage(), e);
                }
            }
        }

        String knowledgeTags = "[]";
        String keyPoints = "";
        String trapTypes = "";
        String relatedKnowledge = "";
        String coreVocab = "";
        String grammarPoints = "";
        String difficulty = null;

        if (hiddenJson != null && !hiddenJson.isEmpty()) {
            // 统一提取知识点/语法标签
            if (hiddenJson.get("knowledge_points") != null) {
                knowledgeTags = JSON.toJSONString(hiddenJson.get("knowledge_points"));
            } else if (hiddenJson.get("grammar_tags") != null) {
                knowledgeTags = JSON.toJSONString(hiddenJson.get("grammar_tags"));
                grammarPoints = knowledgeTags;
            } else if (hiddenJson.get("grammar_points") != null) {
                knowledgeTags = JSON.toJSONString(hiddenJson.get("grammar_points"));
                grammarPoints = knowledgeTags;
            }

            // 提取核心词汇
            if (hiddenJson.get("core_vocab") != null) {
                coreVocab = JSON.toJSONString(hiddenJson.get("core_vocab"));
            }

            // 提取其他元数据
            StringBuilder kpBuilder = new StringBuilder();
            if (hiddenJson.get("difficulty") != null) {
                difficulty = String.valueOf(hiddenJson.get("difficulty"));
                kpBuilder.append("难度：").append(difficulty).append("；");
            }
            if (hiddenJson.get("trap_types") != null) {
                String trapStr = String.valueOf(hiddenJson.get("trap_types"));
                trapTypes = trapStr;
                kpBuilder.append("易错类型：").append(trapStr).append("；");
            }
            if (hiddenJson.get("cross_subject_tags") != null) {
                String crossStr = String.valueOf(hiddenJson.get("cross_subject_tags"));
                relatedKnowledge = crossStr;
                kpBuilder.append("跨科关联：").append(crossStr).append("；");
            }
            if (hiddenJson.get("question_type") != null) {
                kpBuilder.append("问题类型：").append(hiddenJson.get("question_type")).append("；");
            }
            if (hiddenJson.get("compare_pairs") != null) {
                String compareStr = String.valueOf(hiddenJson.get("compare_pairs"));
                kpBuilder.append("易混淆：").append(compareStr).append("；");
            }
            if (hiddenJson.get("related_knowledge") != null) {
                relatedKnowledge = String.valueOf(hiddenJson.get("related_knowledge"));
            }
            if (hiddenJson.get("query_type") != null) {
                kpBuilder.append("查询类型：").append(hiddenJson.get("query_type")).append("；");
            }
            keyPoints = kpBuilder.toString();
        }

        // 如果关键信息仍然缺失，尝试AI二次提取
        if ("[]".equals(knowledgeTags) && keyPoints.isEmpty()) {
            String extractPrompt = "你是一个知识点提取专家。请从以下AI回答中提取核心信息，严格按JSON格式返回，不要返回其他内容：\n" +
                    "{\"knowledge_tags\":[\"标签1\",\"标签2\"],\"key_points\":\"解题要点/语法要点概括(200字以内)\"}\n\n" +
                    "AI回答内容：\n"
                    + (cleanedAiContent != null && cleanedAiContent.length() > 3000
                            ? cleanedAiContent.substring(0, 3000)
                            : cleanedAiContent);

            try {
                String extractResult = aiService.chat("你是知识点提取专家，只返回JSON。",
                        extractPrompt, "note-extract-" + userId);
                if (extractResult != null) {
                    int jsonStart = extractResult.indexOf('{');
                    int jsonEnd = extractResult.lastIndexOf('}');
                    if (jsonStart >= 0 && jsonEnd > jsonStart) {
                        String jsonStr = extractResult.substring(jsonStart, jsonEnd + 1);
                        Map<String, Object> parsed = JSON.parseObject(jsonStr, Map.class);
                        if (parsed.get("knowledge_tags") != null) {
                            knowledgeTags = JSON.toJSONString(parsed.get("knowledge_tags"));
                        }
                        if (parsed.get("key_points") != null) {
                            keyPoints = AiTextCleaner.clean(String.valueOf(parsed.get("key_points")));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("AI知识点提取失败，使用默认值: {}", e.getMessage());
                knowledgeTags = "[\"" + subject + "\"]";
                keyPoints = questionText != null && questionText.length() > 100
                        ? questionText.substring(0, 100)
                        : questionText;
            }
        }

        AiNote existing = getOne(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .eq(AiNote::getGroupId, groupId));

        log.info("检查是否存在已有记录: userId={}, groupId={}, existing={}", userId, groupId, existing != null ? "存在" : "不存在");

        // 智能生成标题
        keyPoints = AiTextCleaner.clean(keyPoints);
        String title = generateTitle(subject, hiddenJson, cleanedAiContent, questionText);

        if (existing != null) {
            log.info("更新已有记录: noteId={}, title={}", existing.getId(), title);
            if (existing.getTitle() == null || existing.getTitle().trim().isEmpty()) {
                existing.setTitle(title);
            }
            existing.setSubject(subject);
            existing.setQuestionText(appendLabeledBlock(existing.getQuestionText(), "追问", questionText));
            existing.setAiContent(appendLabeledBlock(existing.getAiContent(), "追问回答", cleanedAiContent));
            existing.setKnowledgeTags(knowledgeTags);
            existing.setKeyPoints(keyPoints);
            existing.setTrapTypes(trapTypes);
            existing.setRelatedKnowledge(relatedKnowledge);
            existing.setCoreVocab(coreVocab);
            existing.setGrammarPoints(grammarPoints);
            if (difficulty != null && !difficulty.isEmpty()) {
                existing.setDifficulty(difficulty);
            }
            existing.setIsAutoExtracted(1);
            existing.setUpdateTime(java.time.LocalDateTime.now());
            updateFollowUpSummaryAsync(userId, groupId, existing);
            try {
                updateById(existing);
                log.info("更新已有记录成功: noteId={}", existing.getId());
            } catch (Exception e) {
                log.error("更新AiNote记录失败! noteId={}, error={}", existing.getId(), e.getMessage(), e);
                throw e;
            }
            return existing;
        }

        AiNote note = new AiNote();
        note.setUserId(userId);
        note.setSessionId(sessionId);
        note.setGroupId(groupId);
        note.setSubject(subject);
        note.setTitle(title);
        note.setQuestionText(questionText);
        if (imageUrl != null && !imageUrl.isEmpty()) {
            note.setQuestionImage(imageUrl);
        }
        if (articleId != null) {
            note.setSourceArticleId(articleId);
        }
        note.setAiContent(cleanedAiContent);
        note.setKnowledgeTags(knowledgeTags);
        note.setKeyPoints(keyPoints);
        note.setTrapTypes(trapTypes);
        note.setRelatedKnowledge(relatedKnowledge);
        note.setCoreVocab(coreVocab);
        note.setGrammarPoints(grammarPoints);
        if (difficulty != null && !difficulty.isEmpty()) {
            note.setDifficulty(difficulty);
        }
        note.setNoteType("qa");
        note.setSourceType("chat_message");
        note.setIsAutoExtracted(1);
        if (note.getCreateTime() == null) {
            note.setCreateTime(java.time.LocalDateTime.now());
        }
        if (note.getUpdateTime() == null) {
            note.setUpdateTime(java.time.LocalDateTime.now());
        }

        log.info("准备保存新记录: userId={}, subject={}, title={}, questionTextLength={}, aiContentLength={}",
                userId, subject, note.getTitle(),
                note.getQuestionText() != null ? note.getQuestionText().length() : 0,
                note.getAiContent() != null ? note.getAiContent().length() : 0);

        try {
            save(note);
            log.info("保存新记录成功: noteId={}, title={}", note.getId(), note.getTitle());
        } catch (Exception e) {
            log.error("保存AiNote记录失败! userId={}, subject={}, title={}, error={}",
                    userId, subject, note.getTitle(), e.getMessage(), e);
            throw e;
        }

        try {
            String reviewContent = note.getQuestionText() != null ? note.getQuestionText() : note.getTitle();
            String answerContent = buildNoteAnswerContent(note);
            reviewTaskService.createReviewTask(userId, "note", note.getId(), reviewContent, answerContent,
                    note.getSubject());
        } catch (Exception e) {
            log.warn("创建复习任务失败: {}", e.getMessage());
        }

        try {
            learningRecordService.recordLearning(userId, "note", null, note.getTitle(), null);
        } catch (Exception e) {
            log.warn("记录笔记行为失败: {}", e.getMessage());
        }

        return note;
    }

    /**
     * 智能生成笔记标题
     * 规则一：优先提取AI回答中的"题眼定位"摘要
     * 规则二：用知识点标签拼接
     * 规则三：英语句子用原文前几个词
     * 规则四：英语单词用单词本身
     * 规则五：降级方案 - 科目+时间戳
     */
    private String generateTitle(String subject, Map<String, Object> hiddenJson, String aiContent,
            String questionText) {
        // 规则一：优先提取AI回答中的"题眼定位"摘要
        if (aiContent != null && !aiContent.isEmpty()) {
            String[] paragraphs = aiContent.split("\\n\\s*\\n");
            if (paragraphs.length > 0) {
                String firstParagraph = paragraphs[0];
                if (firstParagraph.contains("本题考查的核心知识点是：")) {
                    String summary = firstParagraph.substring(firstParagraph.indexOf("：") + 1).trim();
                    if (summary.contains("。")) {
                        summary = summary.substring(0, summary.indexOf("。"));
                    }
                    return summary.length() > 30 ? summary.substring(0, 30) : summary;
                }
            }
        }

        // 规则二：如果第一段没有明确摘要，用知识点标签拼接
        if (hiddenJson != null) {
            List<String> tags = new ArrayList<>();
            if (hiddenJson.get("knowledge_points") instanceof List) {
                tags.addAll((List<String>) hiddenJson.get("knowledge_points"));
            }
            if (hiddenJson.get("grammar_tags") instanceof List) {
                tags.addAll((List<String>) hiddenJson.get("grammar_tags"));
            }
            if (!tags.isEmpty()) {
                String tagTitle = tags.stream().limit(3).collect(Collectors.joining("与"));
                return tagTitle.length() > 30 ? tagTitle.substring(0, 30) : tagTitle;
            }
        }

        // 规则三 & 四：英语句子用原文前几个词，单词用单词本身
        if ("english".equals(subject) || "reading".equals(subject)) {
            if (hiddenJson != null) {
                String queryType = hiddenJson.get("query_type") != null ? String.valueOf(hiddenJson.get("query_type"))
                        : null;
                if ("word".equals(queryType) && hiddenJson.get("word") != null) {
                    return String.valueOf(hiddenJson.get("word")); // 规则四
                }
                if ("sentence".equals(queryType) && hiddenJson.get("sentence") != null) {
                    String sentence = String.valueOf(hiddenJson.get("sentence"));
                    String[] words = sentence.split("\\s+");
                    String sentenceTitle = Arrays.stream(words).limit(10).collect(Collectors.joining(" ")) + "...";
                    return sentenceTitle.length() > 30 ? sentenceTitle.substring(0, 30) : sentenceTitle; // 规则三
                }
            }
        }

        // 规则五：降级方案
        String subjectLabel = subject != null ? subject : "笔记";
        return subjectLabel + " " + java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .format(java.time.LocalDateTime.now());
    }

    private String appendLabeledBlock(String existing, String label, String value) {
        String cleaned = AiTextCleaner.clean(value);
        if (cleaned == null || cleaned.isEmpty()) {
            return existing;
        }
        if (existing == null || existing.trim().isEmpty()) {
            return cleaned;
        }
        if (existing.contains(cleaned)) {
            return existing;
        }
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .format(java.time.LocalDateTime.now());
        return existing.trim() + "\n\n【" + label + " " + timestamp + "】\n" + cleaned;
    }

    private void updateFollowUpSummaryAsync(Long userId, String groupId, AiNote note) {
        try {
            List<ChatMessage> groupMessages = chatMessageMapper.selectList(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getGroupId, groupId)
                            .eq(ChatMessage::getUserId, userId)
                            .orderByAsc(ChatMessage::getCreateTime));

            long userMsgCount = groupMessages.stream()
                    .filter(m -> "USER".equalsIgnoreCase(m.getRole()))
                    .count();

            if (userMsgCount <= 1) {
                return;
            }

            StringBuilder conversationLog = new StringBuilder();
            for (ChatMessage m : groupMessages) {
                String role = "USER".equalsIgnoreCase(m.getRole()) ? "用户" : "AI";
                String content = m.getContent();
                if (content != null && content.length() > 200) {
                    content = content.substring(0, 200) + "...";
                }
                conversationLog.append(role).append("：").append(content).append("\n");
            }

            String summaryPrompt = "请将以下多轮对话归纳为简洁的追问摘要，保留关键问题和AI的核心回答要点（不超过300字）：\n\n"
                    + conversationLog.toString();

            String summary = aiService.chat("你是归纳总结专家，只输出摘要内容。",
                    summaryPrompt, "followup-" + userId);

            if (summary != null && !summary.isEmpty()) {
                note.setFollowUpSummary(AiTextCleaner.clean(summary));
            }
        } catch (Exception e) {
            log.warn("追问摘要提取失败: {}", e.getMessage());
        }
    }

    @Override
    public AiNote updateByGroupId(Long userId, String groupId, String questionText, String aiContent, String imageUrl) {
        String cleanedAiContent = AiTextCleaner.clean(aiContent);
        AiNote existing = getOne(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .eq(AiNote::getGroupId, groupId));

        if (existing == null) {
            return autoExtractFromChat(userId, null, groupId, "general", questionText, aiContent,
                    (Map<String, Object>) null, imageUrl);
        }

        String extractPrompt = "你是一个知识点提取专家。请从以下AI回答中提取核心信息，严格按JSON格式返回，不要返回其他内容：\n" +
                "{\"knowledge_tags\":[\"标签1\",\"标签2\"],\"key_points\":\"解题要点/语法要点概括(200字以内)\"}\n\n" +
                "AI回答内容：\n"
                + (cleanedAiContent != null && cleanedAiContent.length() > 3000 ? cleanedAiContent.substring(0, 3000)
                        : cleanedAiContent);

        String knowledgeTags = existing.getKnowledgeTags();
        String keyPoints = existing.getKeyPoints();

        try {
            String extractResult = aiService.chat("你是知识点提取专家，只返回JSON。",
                    extractPrompt, "note-extract-" + userId);
            if (extractResult != null) {
                int jsonStart = extractResult.indexOf('{');
                int jsonEnd = extractResult.lastIndexOf('}');
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    String jsonStr = extractResult.substring(jsonStart, jsonEnd + 1);
                    Map<String, Object> parsed = JSON.parseObject(jsonStr, Map.class);
                    if (parsed.get("knowledge_tags") != null) {
                        knowledgeTags = JSON.toJSONString(parsed.get("knowledge_tags"));
                    }
                    if (parsed.get("key_points") != null) {
                        keyPoints = AiTextCleaner.clean(String.valueOf(parsed.get("key_points")));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("AI知识点提取失败: {}", e.getMessage());
        }

        existing.setQuestionText(appendLabeledBlock(existing.getQuestionText(), "追问", questionText));
        existing.setAiContent(appendLabeledBlock(existing.getAiContent(), "追问回答", cleanedAiContent));
        existing.setKnowledgeTags(knowledgeTags);
        existing.setKeyPoints(AiTextCleaner.clean(keyPoints));
        if (imageUrl != null && !imageUrl.isEmpty()) {
            existing.setQuestionImage(imageUrl);
        }
        if (existing.getTitle() == null || existing.getTitle().trim().isEmpty()) {
            if (questionText != null && questionText.length() > 30) {
                existing.setTitle(questionText.substring(0, 30) + "...");
            } else {
                existing.setTitle(questionText);
            }
        }
        existing.setUpdateTime(java.time.LocalDateTime.now());
        updateFollowUpSummaryAsync(userId, groupId, existing);
        updateById(existing);
        return existing;
    }

    @Override
    public AiNote updateByGroupId(Long userId, String groupId, String questionText, String aiContent) {
        return updateByGroupId(userId, groupId, questionText, aiContent, null);
    }

    @Override
    public void deleteByGroupId(Long userId, String groupId) {
        List<AiNote> notes = list(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .eq(AiNote::getGroupId, groupId));
        for (AiNote note : notes) {
            try {
                reviewTaskService.remove(new LambdaQueryWrapper<com.geekyan.entity.ReviewTask>()
                        .eq(com.geekyan.entity.ReviewTask::getUserId, userId)
                        .eq(com.geekyan.entity.ReviewTask::getRelatedType, "note")
                        .eq(com.geekyan.entity.ReviewTask::getRelatedId, note.getId()));
            } catch (Exception e) {
                log.warn("删除关联复习任务失败: noteId={}, error={}", note.getId(), e.getMessage());
            }
        }
        remove(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .eq(AiNote::getGroupId, groupId));
    }

    @Override
    public List<AiNote> getUserNotes(Long userId, String subject) {
        LambdaQueryWrapper<AiNote> wrapper = new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .orderByDesc(AiNote::getCreateTime);
        if (subject != null && !subject.isEmpty()) {
            wrapper.eq(AiNote::getSubject, subject);
        }
        return list(wrapper);
    }

    @Override
    public Map<String, Object> getDailySummary(Long userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        List<AiNote> todayNotes = list(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .ge(AiNote::getCreateTime, todayStart)
                .orderByDesc(AiNote::getCreateTime));

        List<ChatMessage> todayMessages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getUserId, userId)
                        .ge(ChatMessage::getCreateTime, todayStart)
                        .orderByAsc(ChatMessage::getCreateTime));

        Map<String, Integer> subjectCount = new HashMap<>();
        Set<String> allTags = new LinkedHashSet<>();
        for (AiNote note : todayNotes) {
            subjectCount.merge(note.getSubject(), 1, Integer::sum);
            if (note.getKnowledgeTags() != null) {
                try {
                    JSONArray tags = JSON.parseArray(note.getKnowledgeTags());
                    for (int i = 0; i < tags.size(); i++) {
                        allTags.add(tags.getString(i));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        StringBuilder summaryPrompt = new StringBuilder();
        summaryPrompt.append("请根据以下今日学习数据，生成一份简洁的今日学习总结（不超过400字）：\n");
        summaryPrompt.append(String.format("今日提问%d个问题组，涉及科目：%s。\n",
                todayNotes.size(),
                subjectCount.entrySet().stream()
                        .map(e -> e.getKey() + "(" + e.getValue() + "题)")
                        .collect(Collectors.joining("、"))));
        summaryPrompt.append(String.format("知识点标签：%s。\n", String.join("、", allTags)));

        if (!todayNotes.isEmpty()) {
            summaryPrompt.append("\n各问题摘要：\n");
            for (int i = 0; i < Math.min(todayNotes.size(), 10); i++) {
                AiNote note = todayNotes.get(i);
                String q = note.getQuestionText() != null && note.getQuestionText().length() > 80
                        ? note.getQuestionText().substring(0, 80) + "..."
                        : note.getQuestionText();
                summaryPrompt.append(String.format("%d. [%s] %s\n", i + 1, note.getSubject(), q));
            }
        }

        summaryPrompt
                .append("\n请输出：\n1. 今日提问概览\n2. 每个问题的简短摘要和掌握情况预判\n3. 今日暴露的薄弱点汇总\n4. 推荐加入生词本的词汇列表（如果是英语）\n5. 明日复习建议");

        String aiReport;
        try {
            aiReport = aiService.chat("你是考研学习分析专家，只输出有效分析内容。",
                    summaryPrompt.toString(), "daily-summary-" + userId);
        } catch (Exception ex) {
            aiReport = String.format("今日共提问%d个问题，涉及%s。继续坚持学习！",
                    todayNotes.size(),
                    subjectCount.entrySet().stream()
                            .map(entry -> entry.getKey() + entry.getValue() + "题")
                            .collect(Collectors.joining("、")));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalQuestions", todayNotes.size());
        result.put("subjectDistribution", subjectCount);
        result.put("knowledgeTags", new ArrayList<>(allTags));
        result.put("aiSummary", aiReport);
        result.put("notes", todayNotes);
        return result;
    }

    private boolean isWordQuery(String questionText) {
        if (questionText == null || questionText.isEmpty()) {
            return false;
        }
        String trimmed = questionText.trim();
        if (trimmed.length() > 50) {
            return false;
        }
        int letterCount = 0;
        int spaceCount = 0;
        for (char c : trimmed.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                letterCount++;
            } else if (c == ' ') {
                spaceCount++;
            }
        }
        if (letterCount == 0) {
            return false;
        }
        double letterRatio = (double) letterCount / trimmed.length();
        if (letterRatio > 0.7 && spaceCount <= 2) {
            return true;
        }
        if (trimmed.matches("^[a-zA-Z]+(\\s[a-zA-Z]+)?$")) {
            return true;
        }
        return false;
    }

    private void autoAddWordToBook(Long userId, String questionText, String hiddenJson) {
        String word = questionText != null ? questionText.trim() : "";
        if (word.isEmpty()) {
            return;
        }

        WordBook existing = wordBookMapper.selectOne(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .eq(WordBook::getWord, word));
        if (existing != null) {
            log.info("单词已存在于生词本: {}", word);
            return;
        }

        try {
            String targetBookName = resolveUserDefaultBookName(userId);
            wordBookService.addWord(userId, null, word, targetBookName);
            log.info("自动加入生词本: {} -> {}", word, targetBookName);
        } catch (Exception e) {
            log.warn("自动加入生词本失败: word={}, error={}", word, e.getMessage());
        }
    }

    private String resolveUserDefaultBookName(Long userId) {
        return "默认生词本";
    }

    private void autoSaveLongSentence(Long userId, String questionText, String aiContent, String hiddenJson) {
        if (questionText == null || questionText.trim().isEmpty()) {
            return;
        }
        aiContent = AiTextCleaner.clean(aiContent);
        // 从prompt中提取实际句子
        String actualSentence = extractSentenceFromPrompt(questionText.trim());
        if (actualSentence.isEmpty()) {
            log.warn("从prompt中未能提取到有效句子: {}", questionText);
            return;
        }

        LongSentence existing = longSentenceMapper.selectOne(new LambdaQueryWrapper<LongSentence>()
                .eq(LongSentence::getUserId, userId)
                .eq(LongSentence::getSentence, actualSentence));
        if (existing != null) {
            log.info("长难句已存在: {}", actualSentence.substring(0, Math.min(30, actualSentence.length())));
            // 如果已有的记录没有分析，但本次有，则更新
            if ((existing.getAnalysis() == null || existing.getAnalysis().isEmpty()) && aiContent != null
                    && !aiContent.isEmpty()) {
                existing.setAnalysis(aiContent);
                longSentenceMapper.updateById(existing);
                log.info("为已存在的长难句更新了语法分析: {}", existing.getId());
            }
            return;
        }

        // 1. 调用百度翻译获取翻译
        String translation = null;
        try {
            translation = baiduTranslateService.translate(actualSentence, "en", "zh");
        } catch (Exception e) {
            log.warn("调用百度翻译失败: {}", e.getMessage());
        }

        // 2. 调用AI获取语法分析 (aiContent 就是分析结果)
        String analysis = aiContent;

        // 3. 从hiddenJson中提取难度等其他信息 (可选)
        String difficulty = "medium";
        String grammarTags = null;
        String coreVocab = null;
        if (hiddenJson != null && !hiddenJson.isEmpty()) {
            try {
                Map<String, Object> parsed = JSON.parseObject(hiddenJson, Map.class);
                if (parsed.get("difficulty") != null) {
                    difficulty = String.valueOf(parsed.get("difficulty"));
                }
                if (parsed.get("grammar_tags") != null) {
                    grammarTags = AiTextCleaner.clean(JSON.toJSONString(parsed.get("grammar_tags")));
                } else if (parsed.get("grammar_points") != null) {
                    grammarTags = AiTextCleaner.clean(JSON.toJSONString(parsed.get("grammar_points")));
                }
                if (parsed.get("core_vocab") != null) {
                    coreVocab = AiTextCleaner.clean(JSON.toJSONString(parsed.get("core_vocab")));
                }
            } catch (Exception e) {
                log.warn("解析长难句隐藏JSON失败: {}", e.getMessage());
            }
        }

        // 4. 打包并保存
        LongSentence sentence = new LongSentence();
        sentence.setUserId(userId);
        sentence.setSentence(actualSentence);
        sentence.setTranslation(translation); // 使用百度翻译的结果
        sentence.setAnalysis(analysis); // 使用AI分析的结果
        sentence.setDifficulty(difficulty);
        sentence.setGrammarTags(grammarTags);
        sentence.setCoreVocab(coreVocab);
        sentence.setSource("chat");
        sentence.setSentenceType("analysis");
        sentence.setCreateTime(java.time.LocalDateTime.now());
        sentence.setUpdateTime(java.time.LocalDateTime.now());
        longSentenceMapper.insert(sentence);
        log.info("自动保存长难句: {}", actualSentence.substring(0, Math.min(30, actualSentence.length())));

        try {
            String sentenceAnswer = buildSentenceAnswerContent(sentence);
            reviewTaskService.createReviewTask(userId, "sentence", sentence.getId(),
                    sentence.getSentence(), sentenceAnswer, "english");
        } catch (Exception e) {
            log.warn("创建长难句复习任务失败: {}", e.getMessage());
        }
    }

    /**
     * 从AI对话prompt中提取实际英文句子
     * prompt格式通常为："你是一位考研英语语法分析专家...原句：xxx" 或 "请分析以下英语句子...句子：xxx"
     */
    private String extractSentenceFromPrompt(String prompt) {
        // 尝试匹配 "原句：xxx" 或 "原句:xxx" 模式
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("原句[：:]\\s*(.+?)(?:\\n|$)").matcher(prompt);
        if (m1.find()) {
            String extracted = m1.group(1).trim();
            if (!extracted.isEmpty())
                return extracted;
        }
        // 尝试匹配 "句子：xxx" 或 "句子:xxx" 模式
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("句子[：:]\\s*(.+?)(?:\\n|$)").matcher(prompt);
        if (m2.find()) {
            String extracted = m2.group(1).trim();
            if (!extracted.isEmpty())
                return extracted;
        }
        // 尝试匹配 "以下英语句子...：xxx" 或引号内的句子
        java.util.regex.Matcher m3 = java.util.regex.Pattern.compile("[\"\"'](.*?)[\"\"']").matcher(prompt);
        if (m3.find()) {
            String extracted = m3.group(1).trim();
            if (!extracted.isEmpty() && extracted.length() > 5)
                return extracted;
        }
        // 如果prompt较短（<200字符），可能直接就是句子
        if (prompt.length() < 200 && !prompt.contains("请") && !prompt.contains("分析")) {
            return prompt;
        }
        // 降级：返回原文（至少不会丢数据）
        return prompt;
    }

    /**
     * 构建长难句复习任务的答案内容（翻转后展示）
     */
    private String buildSentenceAnswerContent(com.geekyan.entity.LongSentence sentence) {
        StringBuilder sb = new StringBuilder();
        if (sentence.getTranslation() != null && !sentence.getTranslation().isEmpty()) {
            sb.append("翻译：").append(sentence.getTranslation());
        }
        if (sentence.getGrammarTags() != null && !sentence.getGrammarTags().isEmpty()) {
            if (sb.length() > 0)
                sb.append("\n");
            sb.append("语法标签：").append(sentence.getGrammarTags());
        }
        if (sentence.getCoreVocab() != null && !sentence.getCoreVocab().isEmpty()) {
            if (sb.length() > 0)
                sb.append("\n");
            sb.append("核心词汇：").append(sentence.getCoreVocab());
        }
        if (sentence.getAnalysis() != null && !sentence.getAnalysis().isEmpty()) {
            if (sb.length() > 0)
                sb.append("\n");
            String analysis = sentence.getAnalysis().length() > 200
                    ? sentence.getAnalysis().substring(0, 200) + "..."
                    : sentence.getAnalysis();
            sb.append("语法分析：").append(analysis);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 构建笔记复习任务的答案内容（翻转后展示）
     */
    private String buildNoteAnswerContent(AiNote note) {
        StringBuilder sb = new StringBuilder();
        if (note.getKeyPoints() != null && !note.getKeyPoints().isEmpty()) {
            sb.append("解题要点：").append(note.getKeyPoints());
        }
        if (note.getTrapTypes() != null && !note.getTrapTypes().isEmpty()) {
            if (sb.length() > 0)
                sb.append("\n");
            sb.append("避坑点：").append(note.getTrapTypes());
        }
        if (note.getKnowledgeTags() != null && !note.getKnowledgeTags().isEmpty()) {
            if (sb.length() > 0)
                sb.append("\n");
            sb.append("标签：").append(note.getKnowledgeTags());
        }
        if (note.getUserNotes() != null && !note.getUserNotes().isEmpty()) {
            if (sb.length() > 0)
                sb.append("\n");
            sb.append("备注：").append(note.getUserNotes());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
