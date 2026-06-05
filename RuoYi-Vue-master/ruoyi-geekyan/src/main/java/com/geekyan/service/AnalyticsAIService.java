package com.geekyan.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geekyan.entity.*;
import com.geekyan.mapper.*;
import com.ruoyi.common.core.redis.RedisCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AnalyticsAIService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsAIService.class);

    private static final String CACHE_PREFIX = "geekyan:analytics:ai:";
    private static final int CACHE_TTL_HOURS = 24;

    private static final String SYSTEM_PROMPT_DIAGNOSIS = "你是一位顶级的AI考研规划师和学习诊断专家。你的任务是基于学生提供的多维度学习数据，进行一次全面、深度、富有洞察力的学情分析，并生成一份专业的诊断报告。你的风格必须是：数据驱动、逻辑严谨、直击痛点、策略可行。\n\n"
            +
            "你的分析原则：\n" +
            "- 数据驱动，有一说一：所有结论必须基于给出的数据，严禁凭空猜测或编造。如果数据不足，需明确说明\n" +
            "- 一针见血，指出问题本质\n" +
            "- 建议具体可执行，不说\"多做题\"这种废话\n" +
            "- 善于发现跨学科的知识关联，找到表面问题背后的深层原因\n" +
            "- 语气严厉但带有鼓励，让学生意识到问题但不丧失信心\n" +
            "- 如果某科目数据为0，说明该科目暂无学习记录，不要分析该科目\n\n" +
            "你必须严格按照要求的JSON格式输出，不要输出任何其他内容。";

    private static final String SYSTEM_PROMPT_HEATMAP = "你是一位数据科学家和教育心理学专家，擅长从数据可视化中解读学生的知识结构和认知特点。你的任务是解读学习活跃度热力图和跨学科知识关联数据，并生成富有洞察力的分析报告。你必须严格按照要求的JSON格式输出，不要输出任何其他内容。";

    private static final String SYSTEM_PROMPT_SUBJECT = "你是一位考研学科辅导专家，擅长根据学生的学习数据对单一学科进行深度分析。你必须严格按照要求的JSON格式输出，不要输出任何其他内容。";

    private static final String SYSTEM_PROMPT_ENGLISH = "你是一位专业的考研英语辅导老师，擅长通过学习数据精准定位学生的薄弱环节，并提供个性化的提分策略。你的分析风格是：数据驱动、直击痛点、策略具体。你必须严格按照要求的JSON格式输出，不要输出任何其他内容。";

    private static final String SYSTEM_PROMPT_DAILY = "你是一位温暖、智慧且善于鼓励的AI学习伙伴。你的任务是根据学生昨日的学习数据，生成一句简短、个性化且富有启发性的\"每日寄语\"。风格：温暖、积极，像朋友一样。避免说教和陈词滥调。你只输出一句话，不要输出其他任何内容。";

    @Autowired
    private IAiService aiService;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private WordBookMapper wordBookMapper;
    @Autowired
    private AiNoteMapper aiNoteMapper;
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    private SearchHistoryMapper searchHistoryMapper;
    @Autowired
    private LongSentenceMapper longSentenceMapper;
    @Autowired
    private ReadingNoteMapper readingNoteMapper;
    @Autowired
    private ReviewTaskMapper reviewTaskMapper;
    @Autowired
    private LearningRecordMapper learningRecordMapper;

    public Map<String, Object> generateDiagnosisReport(Long userId) {
        String cacheKey = CACHE_PREFIX + "diagnosis:" + userId;
        String cached = getCache(cacheKey);
        if (cached != null) {
            try {
                return JSON.parseObject(cached, Map.class);
            } catch (Exception e) {
                log.warn("诊断报告缓存解析失败: {}", e.getMessage());
            }
        }

        Map<String, Object> learningData = collectLearningData(userId);
        String prompt = buildDiagnosisPrompt(learningData);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", learningData.get("summary"));
        result.put("subjectReports", learningData.get("subjectReports"));

        try {
            String aiResponse = aiService.chat(SYSTEM_PROMPT_DIAGNOSIS, prompt, "analytics-diagnosis-" + userId);
            Map<String, Object> aiReport = parseAIJsonResponse(aiResponse);
            if (aiReport != null && !aiReport.isEmpty()) {
                result.put("aiReport", aiReport);
            } else {
                result.put("aiReport", buildFallbackDiagnosis(learningData));
            }
        } catch (Exception e) {
            log.error("AI诊断报告生成失败: {}", e.getMessage());
            result.put("aiReport", buildFallbackDiagnosis(learningData));
        }

        result.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        setCache(cacheKey, result, CACHE_TTL_HOURS);
        return result;
    }

    public String generateHeatmapInsight(Long userId, Map<String, Integer> heatmapMap) {
        String cacheKey = CACHE_PREFIX + "heatmap-insight:" + userId;
        String cached = getCache(cacheKey);
        if (cached != null)
            return cached;

        String prompt = buildHeatmapPrompt(userId, heatmapMap);
        String insight;
        try {
            String aiResponse = aiService.chat(SYSTEM_PROMPT_HEATMAP, prompt, "analytics-heatmap-" + userId);
            Map<String, Object> parsed = parseAIJsonResponse(aiResponse);
            if (parsed != null && (parsed.containsKey("panoramaScan") || parsed.containsKey("insight"))) {
                // 新版三段式结构：提取panoramaScan作为简短insight，同时保留完整结构
                if (parsed.containsKey("panoramaScan")) {
                    insight = (String) parsed.get("panoramaScan");
                } else {
                    insight = (String) parsed.get("insight");
                }
                if (insight == null || insight.isEmpty()) {
                    insight = aiResponse != null ? aiResponse.trim() : buildFallbackHeatmapInsight(heatmapMap);
                }
            } else {
                insight = aiResponse != null ? aiResponse.trim() : buildFallbackHeatmapInsight(heatmapMap);
            }
        } catch (Exception e) {
            log.error("热力图AI解读生成失败: {}", e.getMessage());
            insight = buildFallbackHeatmapInsight(heatmapMap);
        }

        setCache(cacheKey, insight, CACHE_TTL_HOURS);
        return insight;
    }

    public Map<String, Object> generateSubjectInsight(Long userId, String subject) {
        String cacheKey = CACHE_PREFIX + "subject:" + userId + ":" + subject;
        String cached = getCache(cacheKey);
        if (cached != null) {
            try {
                return JSON.parseObject(cached, Map.class);
            } catch (Exception e) {
                log.warn("学科AI分析缓存解析失败: {}", e.getMessage());
            }
        }

        String prompt = buildSubjectPrompt(userId, subject);
        Map<String, Object> aiInsight;
        try {
            String aiResponse = aiService.chat(SYSTEM_PROMPT_SUBJECT, prompt,
                    "analytics-subject-" + userId + "-" + subject);
            aiInsight = parseAIJsonResponse(aiResponse);
            if (aiInsight == null || aiInsight.isEmpty()) {
                aiInsight = buildFallbackSubjectInsight(subject);
            }
        } catch (Exception e) {
            log.error("学科AI分析生成失败: {}", e.getMessage());
            aiInsight = buildFallbackSubjectInsight(subject);
        }

        setCache(cacheKey, aiInsight, CACHE_TTL_HOURS);
        return aiInsight;
    }

    public Map<String, Object> generateEnglishInsight(Long userId) {
        String cacheKey = CACHE_PREFIX + "english:" + userId;
        String cached = getCache(cacheKey);
        if (cached != null) {
            try {
                return JSON.parseObject(cached, Map.class);
            } catch (Exception e) {
                log.warn("英语AI分析缓存解析失败: {}", e.getMessage());
            }
        }

        String prompt = buildEnglishPrompt(userId);
        Map<String, Object> aiInsight;
        try {
            String aiResponse = aiService.chat(SYSTEM_PROMPT_ENGLISH, prompt, "analytics-english-" + userId);
            aiInsight = parseAIJsonResponse(aiResponse);
            if (aiInsight == null || aiInsight.isEmpty()) {
                aiInsight = buildFallbackEnglishInsight();
            }
        } catch (Exception e) {
            log.error("英语AI分析生成失败: {}", e.getMessage());
            aiInsight = buildFallbackEnglishInsight();
        }

        setCache(cacheKey, aiInsight, CACHE_TTL_HOURS);
        return aiInsight;
    }

    public String generateDailyMessage(Long userId) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String cacheKey = CACHE_PREFIX + "daily-msg:" + userId + ":" + today;
        String cached = getCache(cacheKey);
        if (cached != null)
            return cached;

        String prompt = buildDailyMessagePrompt(userId);
        String message;
        try {
            message = aiService.chat(SYSTEM_PROMPT_DAILY, prompt, "analytics-daily-" + userId + "-" + today);
            if (message != null) {
                message = message.trim();
                if (message.startsWith("\"") && message.endsWith("\"")) {
                    message = message.substring(1, message.length() - 1);
                }
                if (message.length() > 50) {
                    message = message.substring(0, 50);
                }
            } else {
                message = "新的一天，继续加油学习吧！";
            }
        } catch (Exception e) {
            log.error("每日寄语生成失败: {}", e.getMessage());
            message = "新的一天，继续加油学习吧！";
        }

        setCache(cacheKey, message, CACHE_TTL_HOURS);
        return message;
    }

    public Map<String, Object> generateReadingInsight(Long userId, Map<String, Object> readingData) {
        String cacheKey = CACHE_PREFIX + "reading:" + userId;
        String cached = getCache(cacheKey);
        if (cached != null) {
            try {
                return JSON.parseObject(cached, Map.class);
            } catch (Exception e) {
                log.warn("精读AI分析缓存解析失败: {}", e.getMessage());
            }
        }

        String prompt = buildReadingInsightPrompt(userId, readingData);
        Map<String, Object> aiInsight;
        try {
            String systemPrompt = "你是一位考研英语精读辅导专家，擅长根据学生的精读学习数据给出个性化的阅读建议和薄弱点分析。你必须严格按照要求的JSON格式输出，不要输出其他任何内容。";
            String aiResponse = aiService.chat(systemPrompt, prompt, "analytics-reading-" + userId);
            aiInsight = parseAIJsonResponse(aiResponse);
            if (aiInsight == null || aiInsight.isEmpty()) {
                aiInsight = buildFallbackReadingInsight(readingData);
            }
        } catch (Exception e) {
            log.error("精读AI分析生成失败: {}", e.getMessage());
            aiInsight = buildFallbackReadingInsight(readingData);
        }

        setCache(cacheKey, aiInsight, CACHE_TTL_HOURS);
        return aiInsight;
    }

    public void evictAllCache(Long userId) {
        deleteCache(CACHE_PREFIX + "diagnosis:" + userId);
        deleteCache(CACHE_PREFIX + "heatmap-insight:" + userId);
        deleteCache(CACHE_PREFIX + "subject:" + userId + ":*");
        deleteCache(CACHE_PREFIX + "english:" + userId);
        deleteCache(CACHE_PREFIX + "reading:" + userId);
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        deleteCache(CACHE_PREFIX + "daily-msg:" + userId + ":" + today);
    }

    /**
     * 清除所有用户的AI分析缓存（定时任务使用）
     * 通过清除缓存key前缀实现批量清除
     */
    public void evictAllCacheByPrefix() {
        try {
            Collection<String> keys = redisCache.keys(CACHE_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisCache.deleteObject(keys);
                log.info("定时任务：清除了{}个AI分析缓存", keys.size());
            }
        } catch (Exception e) {
            log.warn("批量清除AI分析缓存失败: {}", e.getMessage());
        }
    }

    private Map<String, Object> collectLearningData(Long userId) {
        LocalDateTime monthStart = LocalDateTime.now().minusDays(30);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        long totalRecords = learningRecordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .ge(LearningRecord::getCreateTime, monthStart));
        long totalWords = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId));
        long masteredWords = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .ge(WordBook::getMasteryLevel, 2));
        long totalNotes = aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId));
        long totalSentences = longSentenceMapper.selectCount(new LambdaQueryWrapper<LongSentence>()
                .eq(LongSentence::getUserId, userId));
        long totalReadingNotes = readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>()
                .eq(ReadingNote::getUserId, userId));
        long totalSearches = searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId));
        long totalChatMessages = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getRole, "USER"));

        int streak = calculateStreak(userId);

        double accuracy = 0;
        long completedReviews = reviewTaskMapper.selectCount(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getIsCompleted, 1));
        if (completedReviews > 0) {
            List<ReviewTask> completed = reviewTaskMapper.selectList(new LambdaQueryWrapper<ReviewTask>()
                    .eq(ReviewTask::getUserId, userId)
                    .eq(ReviewTask::getIsCompleted, 1)
                    .isNotNull(ReviewTask::getAccuracyScore));
            if (!completed.isEmpty()) {
                double totalScore = 0;
                for (ReviewTask t : completed) {
                    if (t.getAccuracyScore() != null)
                        totalScore += t.getAccuracyScore();
                }
                accuracy = totalScore / completed.size();
            }
        }

        long totalStudyDays = calculateTotalStudyDays(userId);

        long totalDuration = 0;
        List<LearningRecord> allRecords = learningRecordMapper.selectList(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .isNotNull(LearningRecord::getDuration));
        for (LearningRecord r : allRecords) {
            if (r.getDuration() != null)
                totalDuration += r.getDuration();
        }

        int masteryScore = calculateMasteryScore(totalRecords, totalWords, masteredWords, streak);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalQuestions", totalRecords);
        summary.put("accuracy", Math.round(accuracy * 1000) / 1000.0);
        summary.put("streakDays", streak);
        summary.put("totalStudyDays", totalStudyDays);
        summary.put("totalWords", totalWords);
        summary.put("masteredWords", masteredWords);
        summary.put("totalNotes", totalNotes);
        summary.put("totalSentences", totalSentences);
        summary.put("totalReadingNotes", totalReadingNotes);
        summary.put("totalSearches", totalSearches);
        summary.put("totalChatMessages", totalChatMessages);
        summary.put("totalDurationMinutes", totalDuration / 60);
        summary.put("masteryScore", masteryScore);

        Map<String, Object> subjectReports = new LinkedHashMap<>();
        String[] subjects = { "math", "ds", "co", "os", "cn", "english" };
        String[] labels = { "数学", "数据结构", "计算机组成原理", "操作系统", "计算机网络", "英语" };
        for (int i = 0; i < subjects.length; i++) {
            Map<String, Object> subReport = buildSubjectReport(userId, subjects[i], labels[i], monthStart);
            subjectReports.put(subjects[i], subReport);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("subjectReports", subjectReports);

        // ==================== 学习行为洞察数据 ====================
        Map<String, Object> learningBehavior = buildLearningBehaviorData(userId);
        result.put("learningBehavior", learningBehavior);

        // ==================== 跨学科热力图数据 ====================
        Map<String, Object> crossSubjectHeatmap = buildCrossSubjectHeatmapData(userId);
        result.put("crossSubjectHeatmap", crossSubjectHeatmap);

        return result;
    }

    private Map<String, Object> buildLearningBehaviorData(Long userId) {
        Map<String, Object> behavior = new LinkedHashMap<>();
        LocalDateTime monthStart = LocalDateTime.now().minusDays(30);

        // 1. 高频学习时段分析
        List<LearningRecord> monthRecords = learningRecordMapper.selectList(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .ge(LearningRecord::getCreateTime, monthStart));
        List<ChatMessage> monthChats = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getRole, "USER")
                .ge(ChatMessage::getCreateTime, monthStart));

        Map<Integer, Integer> hourActivity = new HashMap<>();
        for (LearningRecord r : monthRecords) {
            if (r.getCreateTime() != null) {
                hourActivity.merge(r.getCreateTime().getHour(), 1, Integer::sum);
            }
        }
        for (ChatMessage m : monthChats) {
            if (m.getCreateTime() != null) {
                hourActivity.merge(m.getCreateTime().getHour(), 1, Integer::sum);
            }
        }

        String preferredStudyTime = "暂无数据";
        if (!hourActivity.isEmpty()) {
            int peakHour = hourActivity.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(0);
            if (peakHour >= 6 && peakHour < 12)
                preferredStudyTime = "上午（6:00-12:00）";
            else if (peakHour >= 12 && peakHour < 18)
                preferredStudyTime = "下午（12:00-18:00）";
            else if (peakHour >= 18 && peakHour < 24)
                preferredStudyTime = "晚间（18:00-24:00）";
            else
                preferredStudyTime = "深夜（0:00-6:00）";
        }
        behavior.put("preferredStudyTime", preferredStudyTime);

        // 2. 笔记习惯
        long monthNotes = aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .ge(AiNote::getCreateTime, monthStart));
        long monthChatCount = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getRole, "USER")
                .ge(ChatMessage::getCreateTime, monthStart));
        String noteTakingHabit;
        if (monthChatCount == 0)
            noteTakingHabit = "low";
        else {
            double noteRatio = (double) monthNotes / monthChatCount;
            if (noteRatio >= 0.5)
                noteTakingHabit = "high";
            else if (noteRatio >= 0.2)
                noteTakingHabit = "medium";
            else
                noteTakingHabit = "low";
        }
        behavior.put("noteTakingHabit", noteTakingHabit);

        // 3. 复习习惯
        long totalReviewTasks = reviewTaskMapper.selectCount(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId));
        long completedReviews = reviewTaskMapper.selectCount(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getIsCompleted, 1));
        String reviewHabit;
        if (totalReviewTasks == 0)
            reviewHabit = "low";
        else {
            double completionRate = (double) completedReviews / totalReviewTasks;
            if (completionRate >= 0.7)
                reviewHabit = "high";
            else if (completionRate >= 0.3)
                reviewHabit = "medium";
            else
                reviewHabit = "low";
        }
        behavior.put("reviewHabit", reviewHabit);

        // 4. 互动模式
        String interactionPattern;
        if (monthChatCount > monthNotes * 3) {
            interactionPattern = "倾向于直接提问，较少进行错误反思";
        } else if (monthNotes > monthChatCount * 0.5) {
            interactionPattern = "善于总结归纳，学习深度较好";
        } else {
            interactionPattern = "提问与总结较为均衡";
        }
        behavior.put("interactionPattern", interactionPattern);

        // 5. 学习规律性
        long activeDaysInMonth = calculateActiveDaysInPeriod(userId, monthStart, LocalDateTime.now());
        String regularity;
        if (activeDaysInMonth >= 25)
            regularity = "非常规律，几乎每天学习";
        else if (activeDaysInMonth >= 15)
            regularity = "较为规律，大部分天在学习";
        else if (activeDaysInMonth >= 8)
            regularity = "不够规律，存在间歇";
        else
            regularity = "缺乏规律，需加强坚持";
        behavior.put("regularity", regularity);
        behavior.put("activeDaysInMonth", activeDaysInMonth);

        return behavior;
    }

    private long calculateActiveDaysInPeriod(Long userId, LocalDateTime start, LocalDateTime end) {
        long activeDays = 0;
        LocalDate current = start.toLocalDate();
        LocalDate endDay = end.toLocalDate();
        while (!current.isAfter(endDay)) {
            LocalDateTime dayStart = current.atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);
            boolean hasActivity = false;
            if (learningRecordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                    .eq(LearningRecord::getUserId, userId)
                    .ge(LearningRecord::getCreateTime, dayStart)
                    .lt(LearningRecord::getCreateTime, dayEnd)) > 0)
                hasActivity = true;
            if (!hasActivity && chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getUserId, userId)
                    .eq(ChatMessage::getRole, "USER")
                    .ge(ChatMessage::getCreateTime, dayStart)
                    .lt(ChatMessage::getCreateTime, dayEnd)) > 0)
                hasActivity = true;
            if (!hasActivity && aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                    .eq(AiNote::getUserId, userId)
                    .ge(AiNote::getCreateTime, dayStart)
                    .lt(AiNote::getCreateTime, dayEnd)) > 0)
                hasActivity = true;
            if (!hasActivity && searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                    .eq(SearchHistory::getUserId, userId)
                    .ge(SearchHistory::getCreateTime, dayStart)
                    .lt(SearchHistory::getCreateTime, dayEnd)) > 0)
                hasActivity = true;
            if (hasActivity)
                activeDays++;
            current = current.plusDays(1);
        }
        return activeDays;
    }

    private Map<String, Object> buildCrossSubjectHeatmapData(Long userId) {
        Map<String, Object> heatmap = new LinkedHashMap<>();
        String[] subjects = { "math", "ds", "co", "os", "cn", "english" };
        String[] labels = { "数学", "数据结构", "计算机组成原理", "操作系统", "计算机网络", "英语" };

        // 计算学科间关联强度（基于共同出现的知识点标签）
        List<AiNote> allNotes = aiNoteMapper.selectList(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .isNotNull(AiNote::getKnowledgeTags));

        Map<String, Set<String>> subjectTags = new HashMap<>();
        for (String subj : subjects) {
            subjectTags.put(subj, new HashSet<>());
        }
        for (AiNote note : allNotes) {
            String subj = note.getSubject();
            if (subj != null && subjectTags.containsKey(subj) && note.getKnowledgeTags() != null) {
                try {
                    JSONArray tags = JSON.parseArray(note.getKnowledgeTags());
                    for (int i = 0; i < tags.size(); i++) {
                        subjectTags.get(subj).add(tags.getString(i));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        List<Map<String, Object>> heatmapData = new ArrayList<>();
        for (int i = 0; i < subjects.length; i++) {
            for (int j = i + 1; j < subjects.length; j++) {
                Set<String> setA = subjectTags.get(subjects[i]);
                Set<String> setB = subjectTags.get(subjects[j]);
                int value = 0;
                if (!setA.isEmpty() && !setB.isEmpty()) {
                    Set<String> intersection = new HashSet<>(setA);
                    intersection.retainAll(setB);
                    Set<String> union = new HashSet<>(setA);
                    union.addAll(setB);
                    value = union.isEmpty() ? 0 : (int) ((double) intersection.size() / union.size() * 100);
                }
                if (value > 0) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("source", labels[i]);
                    entry.put("target", labels[j]);
                    entry.put("value", value);
                    heatmapData.add(entry);
                }
            }
        }

        heatmap.put("data", heatmapData);
        heatmap.put("subjectList", Arrays.asList(labels));
        return heatmap;
    }

    private Map<String, Object> buildSubjectReport(Long userId, String subject, String label,
            LocalDateTime monthStart) {
        long notes = aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .eq(AiNote::getSubject, subject));
        long monthNotes = aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .eq(AiNote::getSubject, subject)
                .ge(AiNote::getCreateTime, monthStart));

        List<AiNote> subjectNotes = aiNoteMapper.selectList(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .eq(AiNote::getSubject, subject)
                .isNotNull(AiNote::getKnowledgeTags)
                .orderByDesc(AiNote::getCreateTime)
                .last("LIMIT 50"));

        Map<String, Integer> tagCount = new HashMap<>();
        for (AiNote note : subjectNotes) {
            if (note.getKnowledgeTags() != null) {
                try {
                    JSONArray tags = JSON.parseArray(note.getKnowledgeTags());
                    for (int i = 0; i < tags.size(); i++) {
                        tagCount.merge(tags.getString(i), 1, Integer::sum);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        List<String> weakPoints = tagCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<String> strongPoints = tagCount.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("label", label);
        report.put("totalQuestions", monthNotes);
        report.put("notes", notes);
        report.put("weakPoints", weakPoints);
        report.put("strongPoints", strongPoints);
        report.put("topTags", tagCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    Map<String, Object> t = new HashMap<>();
                    t.put("name", e.getKey());
                    t.put("count", e.getValue());
                    return t;
                })
                .collect(Collectors.toList()));

        if ("english".equals(subject)) {
            long wordCount = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                    .eq(WordBook::getUserId, userId));
            long wordMastered = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                    .eq(WordBook::getUserId, userId)
                    .ge(WordBook::getMasteryLevel, 2));
            long sentenceCount = longSentenceMapper.selectCount(new LambdaQueryWrapper<LongSentence>()
                    .eq(LongSentence::getUserId, userId));
            long readingNoteCount = readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>()
                    .eq(ReadingNote::getUserId, userId));

            List<LongSentence> sentences = longSentenceMapper.selectList(new LambdaQueryWrapper<LongSentence>()
                    .eq(LongSentence::getUserId, userId)
                    .isNotNull(LongSentence::getAnalysis)
                    .orderByDesc(LongSentence::getCreateTime)
                    .last("LIMIT 20"));

            List<AiNote> englishNotes = aiNoteMapper.selectList(new LambdaQueryWrapper<AiNote>()
                    .eq(AiNote::getUserId, userId)
                    .eq(AiNote::getSubject, "english")
                    .isNotNull(AiNote::getGrammarPoints)
                    .orderByDesc(AiNote::getCreateTime)
                    .last("LIMIT 20"));
            List<String> grammarPoints = new ArrayList<>();
            for (AiNote note : englishNotes) {
                if (note.getGrammarPoints() != null && !note.getGrammarPoints().isEmpty()) {
                    try {
                        JSONArray gpArr = JSON.parseArray(note.getGrammarPoints());
                        for (int i = 0; i < gpArr.size(); i++) {
                            String gp = gpArr.getString(i);
                            if (gp != null && !gp.isEmpty() && !grammarPoints.contains(gp)) {
                                grammarPoints.add(gp);
                            }
                        }
                    } catch (Exception e) {
                        grammarPoints.add(note.getGrammarPoints());
                    }
                }
            }

            List<String> coreVocab = new ArrayList<>();
            for (AiNote note : englishNotes) {
                if (note.getCoreVocab() != null && !note.getCoreVocab().isEmpty()) {
                    try {
                        JSONArray cvArr = JSON.parseArray(note.getCoreVocab());
                        for (int i = 0; i < cvArr.size(); i++) {
                            String cv = cvArr.getString(i);
                            if (cv != null && !cv.isEmpty() && !coreVocab.contains(cv)) {
                                coreVocab.add(cv);
                            }
                        }
                    } catch (Exception e) {
                        coreVocab.add(note.getCoreVocab());
                    }
                }
            }

            List<SearchHistory> searches = searchHistoryMapper.selectList(new LambdaQueryWrapper<SearchHistory>()
                    .eq(SearchHistory::getUserId, userId)
                    .isNotNull(SearchHistory::getQueryType)
                    .orderByDesc(SearchHistory::getCreateTime)
                    .last("LIMIT 50"));
            Map<String, Integer> typeCount = new HashMap<>();
            for (SearchHistory s : searches) {
                if (s.getQueryType() != null)
                    typeCount.merge(s.getQueryType(), 1, Integer::sum);
            }
            List<String> queryTypes = typeCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            List<WordBook> recentWords = wordBookMapper.selectList(new LambdaQueryWrapper<WordBook>()
                    .eq(WordBook::getUserId, userId)
                    .isNotNull(WordBook::getBookName)
                    .orderByDesc(WordBook::getCreateTime)
                    .last("LIMIT 100"));
            Map<String, Integer> levelCount = new HashMap<>();
            for (WordBook w : recentWords) {
                if (w.getBookName() != null)
                    levelCount.merge(w.getBookName(), 1, Integer::sum);
            }

            report.put("totalWords", wordCount);
            report.put("masteredWords", wordMastered);
            report.put("totalSentences", sentenceCount);
            report.put("totalReadingNotes", readingNoteCount);
            report.put("grammarPoints", grammarPoints);
            report.put("coreVocab", coreVocab);
            report.put("queryTypes", queryTypes);
            report.put("wordBookDistribution", levelCount);
        }

        return report;
    }

    private String buildDiagnosisPrompt(Map<String, Object> learningData) {
        StringBuilder sb = new StringBuilder();
        sb.append("请基于以下用户的多维度学习数据，严格按照五段式结构生成一份深度学情诊断报告。\n\n");
        sb.append("重要提示：如果某科目数据为0，说明用户暂时没有该科目的学习记录，请勿凭空分析该科目的薄弱点，只需在报告中说明缺少数据。\n\n");

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) learningData.get("summary");
        sb.append("## 第一部分：整体学情概览数据\n\n");
        sb.append(String.format("- 近30天总答题数：%s\n", summary.get("totalQuestions")));
        sb.append(String.format("- 整体正确率：%.0f%%\n", ((Double) summary.get("accuracy")) * 100));
        sb.append(String.format("- 连续学习天数：%s\n", summary.get("streakDays")));
        sb.append(String.format("- 累计学习天数：%s\n", summary.get("totalStudyDays")));
        sb.append(String.format("- 生词本词汇总量：%s\n", summary.get("totalWords")));
        sb.append(String.format("- 已掌握词汇：%s\n", summary.get("masteredWords")));
        sb.append(String.format("- 累计笔记数：%s\n", summary.get("totalNotes")));
        sb.append(String.format("- 累计精读时长：%s分钟\n", summary.get("totalDurationMinutes")));
        sb.append(String.format("- 综合掌握度评分：%s\n", summary.get("masteryScore")));
        sb.append(String.format("- AI互动次数：%s\n", summary.get("totalChatMessages")));
        sb.append(String.format("- 查词次数：%s\n", summary.get("totalSearches")));
        sb.append("\n");

        @SuppressWarnings("unchecked")
        Map<String, Object> subjectReports = (Map<String, Object>) learningData.get("subjectReports");

        sb.append("## 第二部分：分学科表现数据\n\n");
        String[] subjectKeys = { "math", "ds", "co", "os", "cn", "english" };
        for (String key : subjectKeys) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sub = (Map<String, Object>) subjectReports.get(key);
            if (sub == null)
                continue;
            sb.append("### ").append(sub.get("label")).append("\n");
            sb.append(String.format("- 近30天笔记数：%s，累计笔记数：%s\n", sub.get("totalQuestions"), sub.get("notes")));

            Object weakPoints = sub.get("weakPoints");
            if (weakPoints != null) {
                @SuppressWarnings("unchecked")
                List<String> wpList = (List<String>) weakPoints;
                if (!wpList.isEmpty()) {
                    sb.append("- 高频知识点（可能薄弱）：").append(String.join("、", wpList)).append("\n");
                }
            }
            Object strongPoints = sub.get("strongPoints");
            if (strongPoints != null) {
                @SuppressWarnings("unchecked")
                List<String> spList = (List<String>) strongPoints;
                if (!spList.isEmpty()) {
                    sb.append("- 低频知识点（可能已掌握）：").append(String.join("、", spList)).append("\n");
                }
            }

            if ("english".equals(key)) {
                sb.append(String.format("- 词汇量：%s，已掌握：%s\n", sub.getOrDefault("totalWords", 0),
                        sub.getOrDefault("masteredWords", 0)));
                sb.append(String.format("- 收藏长难句：%s\n", sub.getOrDefault("totalSentences", 0)));
                sb.append(String.format("- 精读笔记：%s\n", sub.getOrDefault("totalReadingNotes", 0)));
                Object grammarPoints = sub.get("grammarPoints");
                if (grammarPoints != null) {
                    @SuppressWarnings("unchecked")
                    List<String> gpList = (List<String>) grammarPoints;
                    if (!gpList.isEmpty())
                        sb.append("- 语法点分析：").append(String.join("；", gpList)).append("\n");
                }
                Object queryTypes = sub.get("queryTypes");
                if (queryTypes != null) {
                    @SuppressWarnings("unchecked")
                    List<String> qtList = (List<String>) queryTypes;
                    if (!qtList.isEmpty())
                        sb.append("- 主要查询类型：").append(String.join("、", qtList)).append("\n");
                }
                Object wordBookDist = sub.get("wordBookDistribution");
                if (wordBookDist != null) {
                    sb.append("- 单词书分布：").append(wordBookDist).append("\n");
                }
            }
            sb.append("\n");
        }

        // ==================== 学习行为洞察数据 ====================
        @SuppressWarnings("unchecked")
        Map<String, Object> learningBehavior = (Map<String, Object>) learningData.get("learningBehavior");
        if (learningBehavior != null) {
            sb.append("## 第三部分：学习行为与习惯数据\n\n");
            sb.append(String.format("- 高频学习时段：%s\n", learningBehavior.getOrDefault("preferredStudyTime", "暂无数据")));
            sb.append(String.format("- 笔记习惯：%s\n", learningBehavior.getOrDefault("noteTakingHabit", "low")));
            sb.append(String.format("- 复习习惯：%s\n", learningBehavior.getOrDefault("reviewHabit", "low")));
            sb.append(String.format("- 互动模式：%s\n", learningBehavior.getOrDefault("interactionPattern", "暂无数据")));
            sb.append(String.format("- 学习规律性：%s\n", learningBehavior.getOrDefault("regularity", "暂无数据")));
            sb.append(String.format("- 近30天活跃天数：%s\n", learningBehavior.getOrDefault("activeDaysInMonth", 0)));
            sb.append("\n");
        }

        // ==================== 跨学科热力图数据 ====================
        @SuppressWarnings("unchecked")
        Map<String, Object> crossSubjectHeatmap = (Map<String, Object>) learningData.get("crossSubjectHeatmap");
        if (crossSubjectHeatmap != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> heatmapData = (List<Map<String, Object>>) crossSubjectHeatmap.get("data");
            if (heatmapData != null && !heatmapData.isEmpty()) {
                sb.append("## 第四部分：跨学科知识关联数据\n\n");
                for (Map<String, Object> entry : heatmapData) {
                    sb.append(String.format("- %s ↔ %s：关联强度 %s\n",
                            entry.get("source"), entry.get("target"), entry.get("value")));
                }
                sb.append("\n");
            }
        }

        sb.append("## 输出要求（五段式结构）\n\n");

        sb.append("### 第一段：整体学情概览 (Dashboard)\n");
        sb.append("用关键指标（总时长、活跃天数、AI互动次数）对学生的整体学习投入度给出一个简明扼要的评价。点出最值得肯定的一个亮点。\n\n");

        sb.append("### 第二段：分学科表现诊断 (Subject Deep Dive)\n");
        sb.append("对每个有数据的学科进行逐一分析，每个学科必须包含：\n");
        sb.append("- 【投入度分析】：结合笔记数和AI互动次数，评价其在该科的投入水平\n");
        sb.append("- 【掌握度评估】：综合判断掌握度（优/良/中/差）\n");
        sb.append("- 【核心优势】：明确指出该学科目前最稳固的知识板块\n");
        sb.append("- 【首要短板】：精准定位当前最影响提分的1-2个薄弱知识点\n\n");

        sb.append("### 第三段：学习行为与习惯洞察 (Behavioral Insights)\n");
        sb.append("基于学习行为数据，分析学生的学习模式：\n");
        sb.append("- 时间管理：学习时段是否规律？是否存在突击学习？\n");
        sb.append("- 知识内化：笔记和复习习惯如何？是否存在\"学而不思\"或\"学而不固\"？\n");
        sb.append("- 学习方法：与AI的互动模式反映了怎样的学习心态？\n\n");

        sb.append("### 第四段：跨学科知识网络分析 (Knowledge Graph Analysis)\n");
        sb.append("基于跨学科热力图数据，解读学生的知识关联网络：\n");
        sb.append("- 识别强关联：哪些学科之间已经建立了紧密联系\n");
        sb.append("- 发现弱关联：哪些本应强关联的学科之间联系薄弱\n");
        sb.append("- 提出构建建议：如何通过刻意练习来加强弱关联\n\n");

        sb.append("### 第五段：顶层备考策略建议 (Top-Level Strategic Advice)\n");
        sb.append("基于以上所有分析，给出总体备考策略：\n");
        sb.append("- 【时间分配建议】：如何调整各学科的时间投入比例\n");
        sb.append("- 【主攻方向】：下一阶段应集中火力攻克的\"山头\"\n");
        sb.append("- 【学习方法优化】：1-2条最需要改进的学习习惯\n\n");

        sb.append("## 输出JSON格式\n\n");
        sb.append("你必须返回以下JSON结构，不要输出其他任何内容。JSON必须是合法的、可被Java解析的。\n\n");
        sb.append("{\n");
        sb.append("  \"overallAssessment\": \"整体评价，150-200字。概括学习节奏、整体水平，指出最突出的问题和最明显的优势。如果数据不足，需说明。\",\n");
        sb.append("  \"subjectDeepDive\": [\n");
        sb.append("    {\n");
        sb.append("      \"subject\": \"学科名称\",\n");
        sb.append("      \"investmentLevel\": \"投入度评价（高/中/低）\",\n");
        sb.append("      \"masteryLevel\": \"掌握度（优/良/中/差）\",\n");
        sb.append("      \"coreStrength\": \"核心优势，20字以内\",\n");
        sb.append("      \"primaryWeakness\": \"首要短板，20字以内\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"behavioralInsights\": {\n");
        sb.append("    \"timeManagement\": \"时间管理评价，30字以内\",\n");
        sb.append("    \"knowledgeInternalization\": \"知识内化评价，30字以内\",\n");
        sb.append("    \"learningMethod\": \"学习方法评价，30字以内\"\n");
        sb.append("  },\n");
        sb.append("  \"knowledgeGraphAnalysis\": {\n");
        sb.append("    \"strongLinks\": [\"强关联学科对，如'计组与OS'\"],\n");
        sb.append("    \"weakLinks\": [\"弱关联学科对，如'数学与数据结构'\"],\n");
        sb.append("    \"bridgeSuggestion\": \"如何加强弱关联的建议，50字以内\"\n");
        sb.append("  },\n");
        sb.append("  \"weakPointWarnings\": [\n");
        sb.append("    {\n");
        sb.append("      \"subject\": \"学科名称\",\n");
        sb.append("      \"topic\": \"具体薄弱知识点\",\n");
        sb.append("      \"errorRate\": 0.45,\n");
        sb.append("      \"dangerLevel\": \"high/medium/low\",\n");
        sb.append("      \"rootCause\": \"根本原因分析，30-50字。追溯上游知识缺陷或思维误区。\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"suggestions\": [\n");
        sb.append("    {\n");
        sb.append("      \"type\": \"warning/info/tip/encourage\",\n");
        sb.append("      \"content\": \"具体可执行的建议，每条30-60字。warning针对严重问题，info给出学习方法，tip给出技巧，encourage对做得好的方面给予肯定。\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"strategicAdvice\": {\n");
        sb.append("    \"timeAllocation\": \"时间分配建议，50字以内\",\n");
        sb.append("    \"mainFocus\": \"主攻方向，30字以内\",\n");
        sb.append("    \"methodOptimization\": \"学习方法优化建议，50字以内\"\n");
        sb.append("  },\n");
        sb.append("  \"dailyRecommendation\": \"今日具体复习建议，50-80字。精确到知识点、时长和题目数量。\",\n");
        sb.append("  \"crossSubjectInsight\": \"跨学科洞察，50-80字。找出不同学科之间的关联问题。\",\n");
        sb.append("  \"hiddenData\": {\n");
        sb.append("    \"report_version\": \"2.0\",\n");
        sb.append("    \"key_findings\": {\n");
        sb.append("      \"strongest_subject\": \"最强学科\",\n");
        sb.append("      \"weakest_subject\": \"最弱学科\",\n");
        sb.append("      \"main_behavioral_issue\": \"主要行为问题\",\n");
        sb.append("      \"knowledge_network_gap\": \"知识网络缺口\"\n");
        sb.append("    },\n");
        sb.append("    \"priority_actions\": [\"优先行动1\", \"优先行动2\", \"优先行动3\"]\n");
        sb.append("  }\n");
        sb.append("}\n\n");

        sb.append("## 分析要求\n\n");
        sb.append("1. 重点关注笔记数较多的科目和知识点\n");
        sb.append("2. 对于笔记高频但掌握度低的知识点，dangerLevel必须标注为\"high\"\n");
        sb.append("3. 建议数量控制在5条以内，每条建议必须具体可执行\n");
        sb.append("4. 找出至少一个跨学科关联问题\n");
        sb.append("5. 如果某个科目没有数据，对应的weakPointWarnings和subjectDeepDive可以为空\n");
        sb.append("6. 整体评价要包含对连续学习天数和活跃天数的评价\n");
        sb.append("7. 每日推荐要精确到知识点的复习顺序和时长\n");
        sb.append("8. 如果用户数据很少（总笔记数<10），请在overallAssessment中说明\"数据积累不足，以下分析仅供参考\"\n\n");
        sb.append("## 禁止行为\n");
        sb.append("- 禁止编造数据中没有的信息\n");
        sb.append("- 禁止使用\"多做题\"、\"多背单词\"等模糊建议\n");
        sb.append("- 禁止只指出问题不给解决方案\n");
        sb.append("- 禁止对没有数据的科目进行分析\n");
        sb.append("- 禁止输出JSON以外的内容\n");
        sb.append("\n");
        sb.append("## 重要约束\n");
        sb.append("如果某科目数据为0（如答题数为0、笔记数为0、词汇量为0），说明用户暂时没有该科目的学习记录。\n");
        sb.append("请勿凭空分析该科目的薄弱点，只需在overallAssessment中说明\"XX科目暂无学习记录，建议尽快开始\"。\n");
        sb.append("weakPointWarnings中不要包含数据为0的科目。\n");
        sb.append("dailyRecommendation应优先推荐已有学习记录的科目。\n\n");

        return sb.toString();
    }

    private String buildHeatmapPrompt(Long userId, Map<String, Integer> heatmapMap) {
        long totalDays = heatmapMap.values().stream().filter(c -> c > 0).count();
        long totalRecords = heatmapMap.values().stream().mapToLong(Integer::longValue).sum();
        double avgPerDay = totalDays > 0 ? (double) totalRecords / totalDays : 0;

        int maxDay = heatmapMap.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int zeroDays = (int) heatmapMap.values().stream().filter(c -> c == 0).count();

        Map<String, Integer> weekdayActivity = new LinkedHashMap<>();
        String[] dayNames = { "周一", "周二", "周三", "周四", "周五", "周六", "周日" };
        for (String d : dayNames)
            weekdayActivity.put(d, 0);

        LocalDate today = LocalDate.now();
        int[] weekdayCounts = new int[7];
        for (Map.Entry<String, Integer> entry : heatmapMap.entrySet()) {
            if (entry.getValue() > 0) {
                try {
                    LocalDate date = LocalDate.parse(entry.getKey());
                    int dow = date.getDayOfWeek().getValue();
                    String dayName = dayNames[dow - 1];
                    weekdayActivity.merge(dayName, entry.getValue(), Integer::sum);
                    weekdayCounts[dow - 1]++;
                } catch (Exception ignored) {
                }
            }
        }

        int consecutiveActive = 0;
        int tempStreak = 0;
        for (int i = 0; i <= 89; i++) {
            String dateKey = today.minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
            if (heatmapMap.getOrDefault(dateKey, 0) > 0) {
                tempStreak++;
                consecutiveActive = Math.max(consecutiveActive, tempStreak);
            } else {
                tempStreak = 0;
            }
        }

        // 找出最活跃和最不活跃的星期
        String mostActiveDay = weekdayActivity.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("无");
        String leastActiveDay = weekdayActivity.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("无");

        // 判断是否存在突击学习（周末活跃度远高于工作日）
        int weekdayTotal = 0, weekendTotal = 0;
        for (int i = 0; i < 5; i++)
            weekdayTotal += weekdayActivity.get(dayNames[i]);
        for (int i = 5; i < 7; i++)
            weekendTotal += weekdayActivity.get(dayNames[i]);
        boolean isCramming = weekendTotal > weekdayTotal * 0.6;

        StringBuilder sb = new StringBuilder();
        sb.append("请基于以下学习活跃度数据，严格按照三段式结构生成一份学习节奏分析报告。\n\n");
        sb.append("## 热力图数据\n\n");
        sb.append(String.format("- 近90天活跃天数：%d天\n", totalDays));
        sb.append(String.format("- 累计学习记录：%d次\n", totalRecords));
        sb.append(String.format("- 日均学习次数：%.1f次\n", avgPerDay));
        sb.append(String.format("- 单日最高：%d次\n", maxDay));
        sb.append(String.format("- 完全未学习天数：%d天\n", zeroDays));
        sb.append(String.format("- 最长连续学习：%d天\n", consecutiveActive));
        sb.append(String.format("- 最活跃星期：%s\n", mostActiveDay));
        sb.append(String.format("- 最不活跃星期：%s\n", leastActiveDay));
        sb.append(String.format("- 是否存在突击学习：%s\n", isCramming ? "是" : "否"));
        sb.append("- 各星期学习活跃度：\n");
        for (Map.Entry<String, Integer> entry : weekdayActivity.entrySet()) {
            sb.append(String.format("  %s：%d次\n", entry.getKey(), entry.getValue()));
        }
        sb.append("\n");

        sb.append("## 输出要求（三段式结构）\n\n");

        sb.append("### 第一段：学习节奏全景扫描\n");
        sb.append("用一个生动的比喻来描述学习节奏，如\"你的学习节奏就像一首...\"。识别高亮区域（活跃高峰）和暗淡区域（低谷期），解读这代表了什么。\n\n");

        sb.append("### 第二段：核心节奏模式解读\n");
        sb.append("分析学习节奏的规律性：\n");
        sb.append("- 是否存在\"三天打鱼两天晒网\"的现象？\n");
        sb.append("- 周末和工作日的差异如何？是否存在突击学习？\n");
        sb.append("- 连续学习天数说明了什么？\n\n");

        sb.append("### 第三段：节奏优化建议\n");
        sb.append("提供2-3条具体的、可操作的建议，来优化学生的学习节奏。禁止假大空。\n\n");

        sb.append("## 输出JSON格式\n\n");
        sb.append("返回以下JSON结构，不要输出其他任何内容：\n\n");
        sb.append("{\n");
        sb.append("  \"panoramaScan\": \"学习节奏全景扫描，80-120字。用比喻描述，识别高峰和低谷。\",\n");
        sb.append("  \"patternAnalysis\": {\n");
        sb.append("    \"regularity\": \"规律性评价，30字以内\",\n");
        sb.append("    \"weekendVsWeekday\": \"周末vs工作日差异，30字以内\",\n");
        sb.append("    \"streakComment\": \"连续学习天数评价，30字以内\"\n");
        sb.append("  },\n");
        sb.append("  \"optimizationSuggestions\": [\n");
        sb.append("    {\n");
        sb.append("      \"suggestion\": \"具体建议，30-50字\",\n");
        sb.append("      \"expectedBenefit\": \"预期效果，20字以内\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"hiddenData\": {\n");
        sb.append("    \"active_days\": " + totalDays + ",\n");
        sb.append("    \"total_records\": " + totalRecords + ",\n");
        sb.append("    \"is_cramming\": " + isCramming + ",\n");
        sb.append("    \"longest_streak\": " + consecutiveActive + "\n");
        sb.append("  }\n");
        sb.append("}\n\n");

        sb.append("## 注意事项\n");
        sb.append("- 分析要基于数据，不要编造\n");
        sb.append("- 建议要具体，如'周三到周五安排较轻松的任务如单词复习'\n");
        sb.append("- optimizationSuggestions最多3条\n");
        sb.append("- 禁止输出JSON以外的内容\n");

        return sb.toString();
    }

    private String buildSubjectPrompt(Long userId, String subject) {
        String[] labels = { "数学", "数据结构", "计算机组成原理", "操作系统", "计算机网络", "英语" };
        Map<String, String> labelMap = new HashMap<>();
        for (int i = 0; i < new String[] { "math", "ds", "co", "os", "cn", "english" }.length; i++) {
            labelMap.put(new String[] { "math", "ds", "co", "os", "cn", "english" }[i], labels[i]);
        }
        String label = labelMap.getOrDefault(subject, subject);

        long totalNotes = aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .eq(AiNote::getSubject, subject));
        long weekNotes = aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .eq(AiNote::getSubject, subject)
                .ge(AiNote::getCreateTime, LocalDateTime.now().minusDays(7)));

        List<AiNote> notes = aiNoteMapper.selectList(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .eq(AiNote::getSubject, subject)
                .isNotNull(AiNote::getKnowledgeTags)
                .orderByDesc(AiNote::getCreateTime)
                .last("LIMIT 50"));

        Map<String, Integer> tagCount = new HashMap<>();
        for (AiNote note : notes) {
            if (note.getKnowledgeTags() != null) {
                try {
                    JSONArray tags = JSON.parseArray(note.getKnowledgeTags());
                    for (int i = 0; i < tags.size(); i++) {
                        tagCount.merge(tags.getString(i), 1, Integer::sum);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        List<String> topTags = tagCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(e -> e.getKey() + "(" + e.getValue() + "次)")
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("请基于以下用户在【%s】学科的学习数据，进行深度分析。\n\n", label));
        sb.append("## 学科数据\n\n");
        sb.append(String.format("- 累计笔记数：%d\n", totalNotes));
        sb.append(String.format("- 近7天笔记数：%d\n", weekNotes));
        if (!topTags.isEmpty()) {
            sb.append("- 知识点分布：").append(String.join("、", topTags)).append("\n");
        }
        sb.append("\n");

        sb.append("## 输出要求\n\n");
        sb.append("返回以下JSON结构，不要输出其他任何内容：\n\n");
        sb.append("{\n");
        sb.append("  \"masteryRanking\": [\n");
        sb.append("    { \"topic\": \"知识点名称\", \"level\": \"掌握/熟悉/薄弱\", \"description\": \"一句话说明掌握情况\" }\n");
        sb.append("  ],\n");
        sb.append("  \"weakPointAnalysis\": [\n");
        sb.append("    { \"topic\": \"薄弱知识点\", \"rootCause\": \"为什么会薄弱，追溯上游知识缺陷\", \"suggestion\": \"具体改进方法\" }\n");
        sb.append("  ],\n");
        sb.append("  \"crossSubjectRelation\": \"与其他学科的关联建议，50字以内\",\n");
        sb.append("  \"reviewStrategy\": \"该学科专属复习策略，50字以内\"\n");
        sb.append("}\n\n");
        sb.append("## 注意事项\n");
        sb.append("- 如果数据很少，给出鼓励性建议\n");
        sb.append("- 禁止输出JSON以外的内容\n");

        return sb.toString();
    }

    private String buildEnglishPrompt(Long userId) {
        LocalDateTime weekStart = LocalDateTime.now().minusDays(7);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // ==================== 词汇维度 ====================
        long wordCount = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId));
        long masteredWords = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .ge(WordBook::getMasteryLevel, 2));
        long weekWords = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .ge(WordBook::getCreateTime, weekStart));
        long todayWords = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .ge(WordBook::getCreateTime, todayStart));

        // 生词本按词书分类分布
        List<WordBook> allWordBooks = wordBookMapper.selectList(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId));
        Map<String, Long> wordBookDistribution = allWordBooks.stream()
                .filter(w -> w.getBookName() != null && !w.getBookName().isEmpty())
                .collect(Collectors.groupingBy(WordBook::getBookName, Collectors.counting()));

        // 核心考研词汇数量（考研核心词书中的单词）
        long coreVocabCount = allWordBooks.stream()
                .filter(w -> w.getBookName() != null && w.getBookName().contains("考研"))
                .count();

        // 长难词数量（单词长度>=8的）
        long longDifficultWordCount = allWordBooks.stream()
                .filter(w -> w.getWord() != null && w.getWord().length() >= 8)
                .count();

        // 生词掌握度分布
        Map<String, Integer> masteryDist = new HashMap<>();
        for (WordBook w : allWordBooks) {
            String level = w.getMasteryLevel() != null ? String.valueOf(w.getMasteryLevel()) : "0";
            masteryDist.merge(level, 1, Integer::sum);
        }

        // ==================== 阅读维度 ====================
        long readingNotes = readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>()
                .eq(ReadingNote::getUserId, userId));
        long weekReadingNotes = readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>()
                .eq(ReadingNote::getUserId, userId)
                .ge(ReadingNote::getCreateTime, weekStart));

        // 精读文章数（按documentId去重）
        List<ReadingNote> allReadingNotes = readingNoteMapper.selectList(new LambdaQueryWrapper<ReadingNote>()
                .eq(ReadingNote::getUserId, userId)
                .isNotNull(ReadingNote::getDocumentId));
        long totalArticles = allReadingNotes.stream()
                .map(ReadingNote::getDocumentId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .count();

        // 阅读时长（从learning_record估算，或从精读笔记数量推算）
        long totalSearches = searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId));
        long weekSearches = searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId)
                .ge(SearchHistory::getCreateTime, weekStart));

        // 估算阅读速度WPM（基于查词频率和精读笔记数量推算）
        int estimatedWPM = 0;
        if (totalArticles > 0 && totalSearches > 0) {
            // 平均每篇文章查词数越少，说明阅读速度越快
            double avgLookupsPerArticle = (double) totalSearches / totalArticles;
            if (avgLookupsPerArticle <= 3)
                estimatedWPM = 180;
            else if (avgLookupsPerArticle <= 6)
                estimatedWPM = 150;
            else if (avgLookupsPerArticle <= 10)
                estimatedWPM = 120;
            else
                estimatedWPM = 90;
        }

        // ==================== 长难句维度 ====================
        long sentenceCount = longSentenceMapper.selectCount(new LambdaQueryWrapper<LongSentence>()
                .eq(LongSentence::getUserId, userId));
        long weekSentences = longSentenceMapper.selectCount(new LambdaQueryWrapper<LongSentence>()
                .eq(LongSentence::getUserId, userId)
                .ge(LongSentence::getCreateTime, weekStart));

        List<LongSentence> sentences = longSentenceMapper.selectList(new LambdaQueryWrapper<LongSentence>()
                .eq(LongSentence::getUserId, userId)
                .isNotNull(LongSentence::getAnalysis)
                .orderByDesc(LongSentence::getCreateTime)
                .last("LIMIT 10"));
        List<String> grammarList = sentences.stream()
                .map(LongSentence::getAnalysis)
                .filter(a -> a != null && !a.isEmpty())
                .limit(5)
                .collect(Collectors.toList());

        // ==================== 笔记维度 ====================
        long englishAiNotes = aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .eq(AiNote::getSubject, "english"));
        long totalNotes = englishAiNotes + readingNotes;

        // 英语AI对话数量
        long englishChats = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getRole, "USER")
                .eq(ChatMessage::getSubject, "english"));

        // 估算阅读理解正确率（基于review_task）
        long totalReviewTasks = reviewTaskMapper.selectCount(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getSubject, "english"));
        long correctReviews = reviewTaskMapper.selectCount(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getSubject, "english")
                .ge(ReviewTask::getMasteryLevel, 0.6));
        double comprehensionAccuracy = totalReviewTasks > 0 ? (double) correctReviews / totalReviewTasks : 0;

        // ==================== 构建Prompt ====================
        StringBuilder sb = new StringBuilder();
        sb.append("请基于以下用户的英语学习数据，严格按照四段式结构生成一份英语学情分析报告。\n\n");

        sb.append("## 输入数据（JSON格式参考）\n\n");
        sb.append("{\n");
        sb.append(String.format("  \"vocabulary\": {\n"));
        sb.append(String.format("    \"total_searched\": %d,\n", totalSearches));
        sb.append(String.format("    \"new_words_this_week\": %d,\n", weekWords));
        sb.append(String.format("    \"total_word_book\": %d,\n", wordCount));
        sb.append(String.format("    \"mastered_words\": %d,\n", masteredWords));
        sb.append(String.format("    \"core_vocab_count\": %d,\n", coreVocabCount));
        sb.append(String.format("    \"long_difficult_word_count\": %d,\n", longDifficultWordCount));
        sb.append(String.format("    \"word_list_distribution\": %s,\n",
                wordBookDistribution.isEmpty() ? "{}" : JSON.toJSONString(wordBookDistribution)));
        sb.append(String.format("    \"mastery_distribution\": %s\n",
                masteryDist.isEmpty() ? "{}" : JSON.toJSONString(masteryDist)));
        sb.append(String.format("  },\n"));

        sb.append(String.format("  \"reading\": {\n"));
        sb.append(String.format("    \"total_reading_notes\": %d,\n", readingNotes));
        sb.append(String.format("    \"total_articles\": %d,\n", totalArticles));
        sb.append(String.format("    \"estimated_reading_speed_wpm\": %d,\n", estimatedWPM));
        sb.append(String.format("    \"long_sentence_analysis_count\": %d,\n", sentenceCount));
        sb.append(String.format("    \"week_reading_notes\": %d,\n", weekReadingNotes));
        sb.append(String.format("    \"comprehension_accuracy\": %.2f,\n", comprehensionAccuracy));
        sb.append(String.format("    \"total_review_tasks\": %d,\n", totalReviewTasks));
        sb.append(String.format("    \"correct_reviews\": %d\n", correctReviews));
        sb.append(String.format("  },\n"));

        sb.append(String.format("  \"notes\": {\n"));
        sb.append(String.format("    \"total_notes\": %d,\n", totalNotes));
        sb.append(String.format("    \"english_ai_notes\": %d,\n", englishAiNotes));
        sb.append(String.format("    \"reading_notes\": %d,\n", readingNotes));
        sb.append(String.format("    \"english_chats\": %d\n", englishChats));
        sb.append(String.format("  },\n"));

        if (!grammarList.isEmpty()) {
            sb.append(String.format("  \"grammar_points_analyzed\": [\"%s\"]\n",
                    String.join("\", \"", grammarList.stream().limit(5).collect(Collectors.toList()))));
        }
        sb.append("}\n\n");

        sb.append("## 输出要求（四段式结构）\n\n");

        sb.append("### 第一段：总体评价与亮点表扬\n");
        sb.append("以积极的口吻开场，首先肯定学生在学习上的投入和亮点。用一句话总结目前的英语学习状态，并点出最突出的一个优点。\n\n");

        sb.append("### 第二段：核心问题诊断（数据驱动）\n");
        sb.append("精准定位1-2个最主要的薄弱环节，每个问题点都必须有数据支撑。\n");
        sb.append("诊断方向参考：\n");
        sb.append("- 词汇问题：词汇量不足（生词率高、核心词汇占比低）、词汇深度不够（反复查询已知单词）、词汇广度不够（查询集中在某一类词书）\n");
        sb.append("- 阅读问题：阅读速度偏慢（WPM低于150）、长难句解析能力弱（长难句分析多但正确率低）、阅读理解能力不足（正确率低于60%）\n");
        sb.append("- 学习习惯问题：学而不思（查词多阅读多但笔记少）、复习不足（生词本持续增加但掌握率低）\n");
        sb.append("对每个诊断出的问题，必须明确写出\"【问题诊断】\"和\"【数据支撑】\"，引用具体数据。\n\n");

        sb.append("### 第三段：个性化提分策略（具体可执行）\n");
        sb.append("针对第二段诊断出的每一个问题，提供具体、可操作的改进建议。禁止假大空的口号。\n");
        sb.append("策略示例：如果诊断为\"词汇深度不够\"，策略可以是\"每日精研5个已查单词，手写英文例句、词根词缀，并找出1-2个同义词\"。\n\n");

        sb.append("### 第四段：总结与鼓励\n");
        sb.append("用简短的话总结本次分析的核心建议，再次给予鼓励。\n\n");

        sb.append("## 输出JSON格式\n\n");
        sb.append("你必须返回以下JSON结构，不要输出其他任何内容：\n\n");
        sb.append("{\n");
        sb.append("  \"overallPraise\": \"总体评价与亮点表扬，80-120字。积极口吻，点出最突出的优点。\",\n");
        sb.append("  \"coreDiagnosis\": [\n");
        sb.append("    {\n");
        sb.append("      \"diagnosis\": \"问题诊断名称，如'词汇深度不足'\",\n");
        sb.append("      \"dataEvidence\": \"数据支撑，引用具体数字，如'核心考研词汇仅占20%，长难词占比达33%'\",\n");
        sb.append("      \"category\": \"词汇/阅读/学习习惯\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"strategies\": [\n");
        sb.append("    {\n");
        sb.append("      \"targetProblem\": \"对应的诊断问题\",\n");
        sb.append("      \"action\": \"具体可执行的建议，30-60字。精确到方法、工具和时间安排。\",\n");
        sb.append("      \"toolRecommendation\": \"推荐使用的APP功能，如'生词本复习'、'长难句分析'\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"closingEncouragement\": \"总结与鼓励，30-50字\",\n");
        sb.append("  \"hiddenData\": {\n");
        sb.append("    \"subject\": \"英语\",\n");
        sb.append("    \"key_issues\": [\"问题1\", \"问题2\"],\n");
        sb.append("    \"suggested_strategies\": [\"策略1\", \"策略2\"],\n");
        sb.append("    \"overall_evaluation\": \"positive/needs_improvement\"\n");
        sb.append("  }\n");
        sb.append("}\n\n");

        sb.append("## 注意事项\n");
        sb.append("- 如果数据很少（词汇量<50、笔记数<5），请在overallPraise中说明\"数据积累不足，以下分析仅供参考\"，并鼓励多使用功能\n");
        sb.append("- coreDiagnosis最多3条，strategies与diagnosis一一对应\n");
        sb.append("- 禁止编造数据中没有的信息\n");
        sb.append("- 禁止使用\"多背单词\"、\"多阅读\"等模糊建议\n");
        sb.append("- 禁止输出JSON以外的内容\n");

        return sb.toString();
    }

    private String buildDailyMessagePrompt(Long userId) {
        LocalDateTime yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // 昨日学习数据
        long yesterdayRecords = learningRecordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .ge(LearningRecord::getCreateTime, yesterdayStart)
                .lt(LearningRecord::getCreateTime, todayStart));
        long yesterdayWords = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .ge(WordBook::getCreateTime, yesterdayStart)
                .lt(WordBook::getCreateTime, todayStart));
        long yesterdayNotes = aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .ge(AiNote::getCreateTime, yesterdayStart)
                .lt(AiNote::getCreateTime, todayStart));
        long yesterdayChats = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getRole, "USER")
                .ge(ChatMessage::getCreateTime, yesterdayStart)
                .lt(ChatMessage::getCreateTime, todayStart));
        long yesterdaySearches = searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId)
                .ge(SearchHistory::getCreateTime, yesterdayStart)
                .lt(SearchHistory::getCreateTime, todayStart));
        long yesterdayReadingNotes = readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>()
                .eq(ReadingNote::getUserId, userId)
                .ge(ReadingNote::getCreateTime, yesterdayStart)
                .lt(ReadingNote::getCreateTime, todayStart));

        int streak = calculateStreak(userId);

        // 找出昨日最有代表性的学习行为
        String keyAction = "学习";
        if (yesterdayReadingNotes > 0)
            keyAction = "精读了" + yesterdayReadingNotes + "篇文章并做了笔记";
        else if (yesterdayWords >= 5)
            keyAction = "学习了" + yesterdayWords + "个新单词";
        else if (yesterdayChats >= 5)
            keyAction = "与AI进行了" + yesterdayChats + "次深度对话";
        else if (yesterdayNotes > 0)
            keyAction = "记录了" + yesterdayNotes + "条学习笔记";
        else if (yesterdaySearches > 0)
            keyAction = "查询了" + yesterdaySearches + "个单词";

        // 昨日学习总时长（分钟估算）
        long yesterdayStudyMinutes = yesterdayRecords * 5 + yesterdayChats * 3 + yesterdayReadingNotes * 10;

        // 昨日涉及的学科
        List<AiNote> yesterdaySubjectNotes = aiNoteMapper.selectList(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId)
                .ge(AiNote::getCreateTime, yesterdayStart)
                .lt(AiNote::getCreateTime, todayStart)
                .isNotNull(AiNote::getSubject)
                .last("LIMIT 20"));
        Map<String, String> subjectLabels = new HashMap<>();
        subjectLabels.put("math", "数学");
        subjectLabels.put("ds", "数据结构");
        subjectLabels.put("co", "计组");
        subjectLabels.put("os", "操作系统");
        subjectLabels.put("cn", "计网");
        subjectLabels.put("english", "英语");
        List<String> subjectsCovered = yesterdaySubjectNotes.stream()
                .map(n -> subjectLabels.getOrDefault(n.getSubject(), n.getSubject()))
                .distinct()
                .collect(Collectors.toList());

        return String.format(
                "基于用户昨日的学习数据，生成一句个性化每日寄语。\n\n" +
                        "昨日数据：学习时长约%d分钟，新增生词%d个，新增笔记%d条，AI对话%d次，查词%d次，精读笔记%d条。\n" +
                        "昨日关键行为：%s。\n" +
                        "涉及学科：%s。\n" +
                        "连续学习天数：%d天。\n\n" +
                        "要求：只输出一句话，严格控制在50字以内，不要加引号，不要输出其他内容。\n" +
                        "个性化要求：寄语必须与\"昨日关键行为\"紧密相关，如提到具体的学了多少单词或做了什么。\n" +
                        "如果昨日学习量为0，给予温和鼓励，如\"休息是为了更好地出发\"。\n" +
                        "如果连续学习天数较多，给予肯定和鼓励。",
                yesterdayStudyMinutes, yesterdayWords, yesterdayNotes, yesterdayChats,
                yesterdaySearches, yesterdayReadingNotes, keyAction,
                subjectsCovered.isEmpty() ? "无" : String.join("、", subjectsCovered), streak);
    }

    private Map<String, Object> parseAIJsonResponse(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty())
            return null;
        try {
            String jsonStr = aiResponse.trim();
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            } else if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            jsonStr = jsonStr.trim();

            int jsonStart = jsonStr.indexOf("{");
            int jsonEnd = jsonStr.lastIndexOf("}");
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                jsonStr = jsonStr.substring(jsonStart, jsonEnd + 1);
            }

            return JSON.parseObject(jsonStr, Map.class);
        } catch (Exception e) {
            log.warn("AI返回JSON解析失败: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildFallbackDiagnosis(Map<String, Object> learningData) {
        Map<String, Object> report = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) learningData.get("summary");
        long totalNotes = ((Number) summary.getOrDefault("totalNotes", 0)).longValue();
        int streak = ((Number) summary.getOrDefault("streakDays", 0)).intValue();

        if (totalNotes < 10) {
            report.put("overallAssessment", "数据积累不足，以下分析仅供参考。目前学习记录较少，建议多使用AI对话、笔记记录等功能，积累更多数据后可获得精准分析。");
        } else {
            report.put("overallAssessment",
                    String.format("你已累计学习%d天，记录了%d条笔记。继续保持学习节奏，系统会根据更多数据给出更精准的分析。", streak, totalNotes));
        }
        report.put("subjectDeepDive", new ArrayList<>());
        report.put("behavioralInsights", Map.of(
                "timeManagement", "数据积累中",
                "knowledgeInternalization", "数据积累中",
                "learningMethod", "数据积累中"));
        report.put("knowledgeGraphAnalysis", Map.of(
                "strongLinks", new ArrayList<>(),
                "weakLinks", new ArrayList<>(),
                "bridgeSuggestion", "数据积累中，暂无跨学科关联分析。"));
        report.put("weakPointWarnings", new ArrayList<>());
        report.put("suggestions", new ArrayList<>());
        report.put("strategicAdvice", Map.of(
                "timeAllocation", "建议均衡分配各学科学习时间",
                "mainFocus", "数据积累中",
                "methodOptimization", "建议每天坚持学习，积累更多数据"));
        report.put("dailyRecommendation", "建议每天保持学习，积累更多数据后可获得个性化分析。");
        report.put("crossSubjectInsight", "数据积累中，暂无跨学科洞察。");
        Map<String, Object> hiddenData = new LinkedHashMap<>();
        hiddenData.put("report_version", "2.0");
        Map<String, String> keyFindings = new LinkedHashMap<>();
        keyFindings.put("strongest_subject", "数据不足");
        keyFindings.put("weakest_subject", "数据不足");
        keyFindings.put("main_behavioral_issue", "数据积累中");
        keyFindings.put("knowledge_network_gap", "数据积累中");
        hiddenData.put("key_findings", keyFindings);
        hiddenData.put("priority_actions", Collections.singletonList("积累更多学习数据"));
        report.put("hiddenData", hiddenData);
        return report;
    }

    private String buildFallbackHeatmapInsight(Map<String, Integer> heatmapMap) {
        long totalDays = heatmapMap.values().stream().filter(c -> c > 0).count();
        long totalRecords = heatmapMap.values().stream().mapToLong(Integer::longValue).sum();
        if (totalDays == 0)
            return "暂无学习记录，开始你的学习之旅吧！";
        double avgPerDay = (double) totalRecords / totalDays;
        return String.format("近90天共学习%d天，累计%d次记录，日均%.1f次。继续加油！", totalDays, totalRecords, avgPerDay);
    }

    private Map<String, Object> buildFallbackSubjectInsight(String subject) {
        Map<String, Object> insight = new LinkedHashMap<>();
        insight.put("masteryRanking", new ArrayList<>());
        insight.put("weakPointAnalysis", new ArrayList<>());
        insight.put("crossSubjectRelation", "数据积累中，暂无跨学科关联分析。");
        insight.put("reviewStrategy", "建议每天坚持学习，积累更多数据后可获得个性化策略。");
        return insight;
    }

    private Map<String, Object> buildFallbackEnglishInsight() {
        Map<String, Object> insight = new LinkedHashMap<>();
        insight.put("overallPraise", "数据积累中，暂无法全面评估。坚持每日学习英语，系统会根据更多数据给出精准分析。");
        List<Map<String, Object>> coreDiagnosis = new ArrayList<>();
        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("diagnosis", "数据不足");
        diag.put("dataEvidence", "当前英语学习数据较少，无法精准诊断");
        diag.put("category", "学习习惯");
        coreDiagnosis.add(diag);
        insight.put("coreDiagnosis", coreDiagnosis);
        List<Map<String, Object>> strategies = new ArrayList<>();
        Map<String, Object> strat = new LinkedHashMap<>();
        strat.put("targetProblem", "数据不足");
        strat.put("action", "每天使用AI英语对话、查词和精读功能，积累更多学习数据");
        strat.put("toolRecommendation", "AI英语私教");
        strategies.add(strat);
        insight.put("strategies", strategies);
        insight.put("closingEncouragement", "坚持每日学习，数据积累后可获得精准分析！");
        Map<String, Object> hiddenData = new LinkedHashMap<>();
        hiddenData.put("subject", "英语");
        hiddenData.put("key_issues", Collections.singletonList("数据不足"));
        hiddenData.put("suggested_strategies", Collections.singletonList("积累更多学习数据"));
        hiddenData.put("overall_evaluation", "needs_improvement");
        insight.put("hiddenData", hiddenData);
        return insight;
    }

    private int calculateStreak(Long userId) {
        LocalDate today = LocalDate.now();
        int streak = 0;
        for (int i = 0; i < 365; i++) {
            LocalDate date = today.minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            boolean hasActivity = false;
            if (learningRecordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                    .eq(LearningRecord::getUserId, userId)
                    .ge(LearningRecord::getCreateTime, dayStart)
                    .lt(LearningRecord::getCreateTime, dayEnd)) > 0) {
                hasActivity = true;
            }
            if (!hasActivity && wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                    .eq(WordBook::getUserId, userId)
                    .ge(WordBook::getCreateTime, dayStart)
                    .lt(WordBook::getCreateTime, dayEnd)) > 0) {
                hasActivity = true;
            }
            if (!hasActivity && aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                    .eq(AiNote::getUserId, userId)
                    .ge(AiNote::getCreateTime, dayStart)
                    .lt(AiNote::getCreateTime, dayEnd)) > 0) {
                hasActivity = true;
            }
            if (!hasActivity && searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                    .eq(SearchHistory::getUserId, userId)
                    .ge(SearchHistory::getCreateTime, dayStart)
                    .lt(SearchHistory::getCreateTime, dayEnd)) > 0) {
                hasActivity = true;
            }

            if (hasActivity) {
                streak++;
            } else if (i > 0) {
                break;
            }
        }
        return streak;
    }

    private long calculateTotalStudyDays(Long userId) {
        LocalDate minDate = null;
        List<LearningRecord> records = learningRecordMapper.selectList(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .orderByAsc(LearningRecord::getCreateTime)
                .last("LIMIT 1"));
        if (!records.isEmpty() && records.get(0).getCreateTime() != null) {
            minDate = records.get(0).getCreateTime().toLocalDate();
        }
        if (minDate == null)
            return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(minDate, LocalDate.now()) + 1;
    }

    private int calculateMasteryScore(long totalRecords, long totalWords, long masteredWords, int streak) {
        int base = 20;
        base += Math.min(30, (int) totalRecords);
        base += Math.min(20, (int) (totalWords > 0 ? (masteredWords * 20 / totalWords) : 0));
        base += Math.min(15, streak);
        base += Math.min(15, (int) (totalWords / 50));
        return Math.max(0, Math.min(100, base));
    }

    private String getCache(String key) {
        try {
            return redisCache.getCacheObject(key);
        } catch (Exception e) {
            log.warn("Redis缓存读取失败: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    private void setCache(String key, Object data, long ttlHours) {
        try {
            String json = data instanceof String ? (String) data : JSON.toJSONString(data);
            redisCache.setCacheObject(key, json, (int) ttlHours, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis缓存写入失败: key={}, error={}", key, e.getMessage());
        }
    }

    private void deleteCache(String key) {
        try {
            redisCache.deleteObject(key);
        } catch (Exception e) {
            log.warn("Redis缓存删除失败: key={}, error={}", key, e.getMessage());
        }
    }

    private String buildReadingInsightPrompt(Long userId, Map<String, Object> readingData) {
        StringBuilder sb = new StringBuilder();
        sb.append("请基于以下用户的精读学习数据，进行个性化分析。\n\n");

        // 概览数据
        Map<String, Object> overview = (Map<String, Object>) readingData.get("overview");
        if (overview != null) {
            sb.append("## 基础数据\n\n");
            sb.append(String.format("- 已读文章数：%s\n", overview.getOrDefault("readingCount", 0)));
            sb.append(String.format("- 精读笔记总数：%s\n", overview.getOrDefault("totalNotes", 0)));
            sb.append(String.format("- 生词本单词数：%s\n", overview.getOrDefault("wordBookCount", 0)));
            sb.append(String.format("- 长难句收藏数：%s\n", overview.getOrDefault("sentenceCount", 0)));
            sb.append("\n");
        }

        // 笔记类型分布
        Map<String, Long> noteTypeDist = (Map<String, Long>) readingData.get("noteTypeDistribution");
        if (noteTypeDist != null && !noteTypeDist.isEmpty()) {
            sb.append("## 笔记类型分布\n\n");
            noteTypeDist.forEach((type, count) -> sb.append(String.format("- %s：%d条\n", type, count)));
            sb.append("\n");
        }

        // 词汇维度
        Map<String, Object> vocab = (Map<String, Object>) readingData.get("vocabularyInReading");
        if (vocab != null) {
            sb.append("## 词汇维度\n\n");
            sb.append(String.format("- 精读中查词次数：%s\n", vocab.getOrDefault("totalQueried", 0)));
            sb.append(String.format("- 查询后加入生词本：%s\n", vocab.getOrDefault("fromReadingCount", 0)));
            List<String> topWords = (List<String>) vocab.get("topQueriedWords");
            if (topWords != null && !topWords.isEmpty()) {
                sb.append("- 高频查询词：").append(String.join("、", topWords.subList(0, Math.min(5, topWords.size()))))
                        .append("\n");
            }
            sb.append("\n");
        }

        // 长难句难度分布
        Map<String, Long> sentDiff = (Map<String, Long>) readingData.get("sentenceDifficultyDistribution");
        if (sentDiff != null && !sentDiff.isEmpty()) {
            sb.append("## 长难句难度分布\n\n");
            sentDiff.forEach((diff, count) -> sb.append(String.format("- %s：%d句\n", diff, count)));
            sb.append("\n");
        }

        // 阅读时段
        Map<String, Long> timeDist = (Map<String, Long>) readingData.get("noteTimeDistribution");
        if (timeDist != null && !timeDist.isEmpty()) {
            sb.append("## 笔记时段分布\n\n");
            timeDist.forEach((period, count) -> sb.append(String.format("- %s：%d条\n", period, count)));
            sb.append("\n");
        }

        sb.append("## 输出要求\n\n");
        sb.append("返回以下JSON结构，不要输出其他任何内容：\n\n");
        sb.append("{\n");
        sb.append("  \"overallAssessment\": \"精读习惯总体评价，50字以内\",\n");
        sb.append("  \"weakPointWarnings\": [\n");
        sb.append("    {\"topic\": \"薄弱点名称\", \"rootCause\": \"原因分析，30字以内\"},\n");
        sb.append("    {\"topic\": \"薄弱点名称\", \"rootCause\": \"原因分析，30字以内\"}\n");
        sb.append("  ],\n");
        sb.append("  \"suggestions\": [\n");
        sb.append("    {\"type\": \"tip\", \"content\": \"具体可执行的建议，30字以内\"},\n");
        sb.append("    {\"type\": \"warning\", \"content\": \"需要警惕的问题，30字以内\"},\n");
        sb.append("    {\"type\": \"strength\", \"content\": \"值得保持的优点，30字以内\"}\n");
        sb.append("  ],\n");
        sb.append("  \"dailyRecommendation\": \"今日精读建议，30字以内\",\n");
        sb.append("  \"grammarWeakPoints\": \"语法薄弱点识别与建议，60字以内\",\n");
        sb.append("  \"readingSuggestion\": \"阅读策略改进建议，60字以内\",\n");
        sb.append("  \"vocabSuggestion\": \"词汇积累建议，60字以内\",\n");
        sb.append("  \"noteQualityTip\": \"笔记质量提升建议，50字以内\"\n");
        sb.append("}\n\n");
        sb.append("## 注意事项\n");
        sb.append("- weakPointWarnings最多3条，从知识点标签和长难句难度中识别\n");
        sb.append("- suggestions最多4条，type只能是tip/warning/strength之一\n");
        sb.append("- 如果数据很少，给出鼓励性建议\n");
        sb.append("- 禁止输出JSON以外的内容\n");

        return sb.toString();
    }

    private Map<String, Object> buildFallbackReadingInsight(Map<String, Object> readingData) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        Map<String, Object> overview = (Map<String, Object>) readingData.get("overview");
        long noteCount = overview != null ? ((Number) overview.getOrDefault("totalNotes", 0)).longValue() : 0;
        long articleCount = overview != null ? ((Number) overview.getOrDefault("readingCount", 0)).longValue() : 0;

        if (noteCount == 0) {
            fallback.put("overallAssessment", "暂无精读记录，建议每天精读1-2篇文章并做笔记。");
        } else if (articleCount < 3) {
            fallback.put("overallAssessment", "精读量较少，建议增加阅读篇数，保持每天精读习惯。");
        } else {
            fallback.put("overallAssessment", "精读习惯良好，继续保持并注意笔记质量。");
        }

        List<Map<String, String>> warnings = new ArrayList<>();
        Map<String, Long> sentDiff = (Map<String, Long>) readingData.get("sentenceDifficultyDistribution");
        if (sentDiff != null && sentDiff.containsKey("hard") && sentDiff.get("hard") > 2) {
            warnings.add(Map.of("topic", "复杂长难句", "rootCause", "困难句较多，语法基础需加强"));
        }
        if (warnings.isEmpty()) {
            warnings.add(Map.of("topic", "语法分析", "rootCause", "建议多收藏长难句进行语法分析"));
        }
        fallback.put("weakPointWarnings", warnings);

        List<Map<String, String>> suggestions = new ArrayList<>();
        suggestions.add(Map.of("type", "tip", "content", "每天精读1篇，做3-5条笔记"));
        suggestions.add(Map.of("type", "strength", "content", "坚持查词并加入生词本是好习惯"));
        fallback.put("suggestions", suggestions);

        fallback.put("dailyRecommendation", "今天试试精读一篇新文章并做分析笔记");
        fallback.put("grammarWeakPoints", "建议多收藏和分析长难句，系统会自动识别语法薄弱点。");
        fallback.put("readingSuggestion", "建议先抓文章主旨再精读细节，注意段落间的逻辑关系。");
        fallback.put("vocabSuggestion", "精读中遇到生词及时查词并加入生词本，定期复习。");
        fallback.put("noteQualityTip", "多做分析笔记和翻译笔记，有助于深度理解文章。");
        return fallback;
    }
}
