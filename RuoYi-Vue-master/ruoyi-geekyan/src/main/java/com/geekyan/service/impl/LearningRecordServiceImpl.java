package com.geekyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.geekyan.entity.*;
import com.geekyan.mapper.*;
import com.geekyan.service.IAiService;
import com.geekyan.service.ILearningRecordService;
import com.geekyan.service.QueryCacheService;
import com.geekyan.service.AnalyticsAIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord>
                implements ILearningRecordService {

        private static final Logger log = LoggerFactory.getLogger(LearningRecordServiceImpl.class);

        private static final Map<String, String> SUBJECT_LABELS = new LinkedHashMap<>();
        static {
                SUBJECT_LABELS.put("english", "英语");
                SUBJECT_LABELS.put("math", "高数");
                SUBJECT_LABELS.put("ds", "数据结构");
                SUBJECT_LABELS.put("os", "操作系统");
                SUBJECT_LABELS.put("co", "计算机组成原理");
                SUBJECT_LABELS.put("cn", "计算机网络");
                SUBJECT_LABELS.put("reading", "精读");
                SUBJECT_LABELS.put("general", "通用");
        }

        @Autowired
        private WordBookMapper wordBookMapper;
        @Autowired
        private AiNoteMapper aiNoteMapper;
        @Autowired
        private ChatMessageMapper chatMessageMapper;
        @Autowired
        private ChatSessionMapper chatSessionMapper;
        @Autowired
        private SearchHistoryMapper searchHistoryMapper;
        @Autowired
        private LongSentenceMapper longSentenceMapper;
        @Autowired
        private ReadingNoteMapper readingNoteMapper;
        @Autowired
        private ReviewTaskMapper reviewTaskMapper;
        @Autowired
        private IAiService aiService;
        @Autowired
        private QueryCacheService queryCacheService;
        @Autowired
        private AnalyticsAIService analyticsAIService;
        @Autowired
        private PdfDocumentMapper pdfDocumentMapper;
        @Autowired
        private ReadingSessionMapper readingSessionMapper;

        @Override
        public void recordLearning(Long userId, String type, Integer duration, String summary, Integer score) {
                recordLearning(userId, type, duration, summary, score, null, type, null);
        }

        @Override
        public void recordLearning(Long userId, String type, Integer duration, String summary, Integer score,
                        String subject, String sourceType, String sourceId) {
                LearningRecord record = new LearningRecord();
                record.setUserId(userId);
                record.setRecordType(type);
                record.setSubject(subject);
                record.setSourceType(sourceType);
                record.setSourceId(sourceId);
                record.setDuration(duration);
                record.setContentSummary(summary);
                record.setScore(score);
                record.setCreateTime(LocalDateTime.now());
                record.setUpdateTime(LocalDateTime.now());
                save(record);
        }

        @Override
        public Map<String, Object> getDashboard(Long userId) {
                LocalDateTime todayStart = LocalDate.now().atStartOfDay();
                LocalDateTime weekStart = LocalDateTime.now().minusDays(7);
                LocalDateTime monthStart = LocalDateTime.now().minusDays(30);

                // 1. 汇总所有相关活动，而不是只统计 learning_record
                // -- 学习记录 --
                long totalLearningRecords = count(
                                new LambdaQueryWrapper<LearningRecord>().eq(LearningRecord::getUserId, userId));
                long todayLearningRecords = count(
                                new LambdaQueryWrapper<LearningRecord>().eq(LearningRecord::getUserId, userId)
                                                .ge(LearningRecord::getCreateTime, todayStart));
                long weekLearningRecords = count(
                                new LambdaQueryWrapper<LearningRecord>().eq(LearningRecord::getUserId, userId)
                                                .ge(LearningRecord::getCreateTime, weekStart));

                // -- 聊天消息 --
                long totalChatMessages = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getUserId, userId).eq(ChatMessage::getRole, "USER"));
                long todayChatMessages = chatMessageMapper
                                .selectCount(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getUserId, userId)
                                                .eq(ChatMessage::getRole, "USER")
                                                .ge(ChatMessage::getCreateTime, todayStart));
                long weekChatMessages = chatMessageMapper
                                .selectCount(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getUserId, userId)
                                                .eq(ChatMessage::getRole, "USER")
                                                .ge(ChatMessage::getCreateTime, weekStart));

                // -- 查词历史 --
                long totalSearches = searchHistoryMapper
                                .selectCount(new LambdaQueryWrapper<SearchHistory>().eq(SearchHistory::getUserId,
                                                userId));
                long todaySearches = searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                                .eq(SearchHistory::getUserId, userId).ge(SearchHistory::getCreateTime, todayStart));
                long weekSearches = searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                                .eq(SearchHistory::getUserId, userId).ge(SearchHistory::getCreateTime, weekStart));

                // -- 精读笔记 --
                long totalReadingNotes = readingNoteMapper
                                .selectCount(new LambdaQueryWrapper<ReadingNote>().eq(ReadingNote::getUserId, userId));
                long todayReadingNotes = readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>()
                                .eq(ReadingNote::getUserId, userId).ge(ReadingNote::getCreateTime, todayStart));
                long weekReadingNotes = readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>()
                                .eq(ReadingNote::getUserId, userId).ge(ReadingNote::getCreateTime, weekStart));

                // -- 汇总成 "积累练习" (totalQuestions) --
                long totalActivities = totalLearningRecords + totalChatMessages + totalSearches + totalReadingNotes;
                long todayActivities = todayLearningRecords + todayChatMessages + todaySearches + todayReadingNotes;
                long weekActivities = weekLearningRecords + weekChatMessages + weekSearches + weekReadingNotes;

                // -- 单词本 --
                long totalWords = wordBookMapper
                                .selectCount(new LambdaQueryWrapper<WordBook>().eq(WordBook::getUserId, userId));
                long masteredWords = wordBookMapper.selectCount(
                                new LambdaQueryWrapper<WordBook>().eq(WordBook::getUserId, userId)
                                                .ge(WordBook::getMasteryLevel, 2));
                long todayWords = wordBookMapper
                                .selectCount(new LambdaQueryWrapper<WordBook>().eq(WordBook::getUserId, userId)
                                                .ge(WordBook::getCreateTime, todayStart));
                long thisWeekWords = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                                .eq(WordBook::getUserId, userId).ge(WordBook::getCreateTime, weekStart));

                // -- 笔记 (合并 AI笔记 和 精读笔记) --
                long totalAiNotes = aiNoteMapper
                                .selectCount(new LambdaQueryWrapper<AiNote>().eq(AiNote::getUserId, userId));
                long todayAiNotes = aiNoteMapper.selectCount(
                                new LambdaQueryWrapper<AiNote>().eq(AiNote::getUserId, userId).ge(AiNote::getCreateTime,
                                                todayStart));
                long totalNotes = totalAiNotes + totalReadingNotes;
                long todayNotes = todayAiNotes + todayReadingNotes;

                // -- 长难句 --
                long totalSentences = longSentenceMapper
                                .selectCount(new LambdaQueryWrapper<LongSentence>().eq(LongSentence::getUserId,
                                                userId));

                // 2. 正确率和阅读时长
                int streak = calculateStreak(userId);

                double accuracy = 0;
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

                int masteryScore = calculateOverallScore(totalActivities, totalWords, masteredWords, streak);

                long totalDuration = 0;
                List<LearningRecord> allRecords = list(new LambdaQueryWrapper<LearningRecord>()
                                .eq(LearningRecord::getUserId, userId).isNotNull(LearningRecord::getDuration));
                for (LearningRecord r : allRecords) {
                        if (r.getDuration() != null)
                                totalDuration += r.getDuration();
                }
                long todayDuration = 0;
                List<LearningRecord> todayRecList = list(
                                new LambdaQueryWrapper<LearningRecord>().eq(LearningRecord::getUserId, userId)
                                                .ge(LearningRecord::getCreateTime, todayStart)
                                                .isNotNull(LearningRecord::getDuration));
                for (LearningRecord r : todayRecList) {
                        if (r.getDuration() != null)
                                todayDuration += r.getDuration();
                }

                // 3. 填充数据到 summary
                Map<String, Object> summary = new HashMap<>();
                summary.put("totalQuestions", totalActivities);
                summary.put("todayQuestions", todayActivities);
                summary.put("weekQuestions", weekActivities);
                summary.put("accuracy", accuracy);
                summary.put("streakDays", streak);
                summary.put("totalWords", totalWords);
                summary.put("masteredWords", masteredWords);
                summary.put("todayWords", todayWords);
                summary.put("totalNotes", totalNotes);
                summary.put("todayNotes", todayNotes);
                summary.put("totalSearches", totalSearches);
                summary.put("todaySearches", todaySearches);
                summary.put("totalSentences", totalSentences);
                summary.put("totalReadingNotes", totalReadingNotes);
                summary.put("totalChatMessages", totalChatMessages);
                summary.put("totalDurationMinutes", totalDuration / 60);
                summary.put("todayDurationMinutes", todayDuration / 60);
                summary.put("masteryScore", masteryScore);

                Map<String, Object> result = new HashMap<>();
                result.put("summary", summary);

                Map<String, Object> subjectBreakdown = new LinkedHashMap<>();
                for (String subject : Arrays.asList("english", "math", "ds", "os", "co", "cn")) {
                        Map<String, Object> subData = getSubjectBriefStats(userId, subject);
                        subjectBreakdown.put(subject, subData);
                }
                result.put("subjects", subjectBreakdown);

                Map<String, Object> countdown = new HashMap<>();
                LocalDate examDate = LocalDate.of(2026, 12, 26);
                LocalDate today = LocalDate.now();
                long daysUntilExam = java.time.temporal.ChronoUnit.DAYS.between(today, examDate);
                countdown.put("examDate", examDate.toString());
                countdown.put("daysUntilExam", daysUntilExam);
                countdown.put("examName", "2027年全国硕士研究生招生考试");
                result.put("countdown", countdown);

                try {
                        String dailyMessage = analyticsAIService.generateDailyMessage(userId);
                        result.put("dailyMessage", dailyMessage);
                } catch (Exception e) {
                        log.warn("AI每日寄语生成失败: {}", e.getMessage());
                }

                // 4. 修正 "个人成长对比" 的计算错误
                Map<String, Object> growthComparison = new HashMap<>();
                LocalDateTime lastWeekStart = LocalDateTime.now().minusDays(14);
                LocalDateTime lastWeekEnd = LocalDateTime.now().minusDays(7);

                // -- 上周活动量（汇总多表） --
                long lastWeekLearningRecords = count(new LambdaQueryWrapper<LearningRecord>()
                                .eq(LearningRecord::getUserId, userId).ge(LearningRecord::getCreateTime, lastWeekStart)
                                .lt(LearningRecord::getCreateTime, lastWeekEnd));
                long lastWeekChatMessages = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getUserId, userId).eq(ChatMessage::getRole, "USER")
                                .ge(ChatMessage::getCreateTime, lastWeekStart)
                                .lt(ChatMessage::getCreateTime, lastWeekEnd));
                long lastWeekSearches = searchHistoryMapper
                                .selectCount(new LambdaQueryWrapper<SearchHistory>()
                                                .eq(SearchHistory::getUserId, userId)
                                                .ge(SearchHistory::getCreateTime, lastWeekStart)
                                                .lt(SearchHistory::getCreateTime, lastWeekEnd));
                long lastWeekReadingNotes = readingNoteMapper
                                .selectCount(new LambdaQueryWrapper<ReadingNote>().eq(ReadingNote::getUserId, userId)
                                                .ge(ReadingNote::getCreateTime, lastWeekStart)
                                                .lt(ReadingNote::getCreateTime, lastWeekEnd));
                long lastWeekActivities = lastWeekLearningRecords + lastWeekChatMessages + lastWeekSearches
                                + lastWeekReadingNotes;

                double practiceChangeWow = lastWeekActivities > 0
                                ? Math.round(((double) (weekActivities - lastWeekActivities) / lastWeekActivities)
                                                * 1000) / 10.0
                                : (weekActivities > 0 ? 100.0 : 0.0);

                // -- 上周单词量 & 修正周环比计算（用 thisWeekWords 而非 todayWords） --
                long lastWeekWords = wordBookMapper
                                .selectCount(new LambdaQueryWrapper<WordBook>().eq(WordBook::getUserId, userId)
                                                .ge(WordBook::getCreateTime, lastWeekStart)
                                                .lt(WordBook::getCreateTime, lastWeekEnd));
                double wordsChangeWow = lastWeekWords > 0
                                ? Math.round(((double) (thisWeekWords - lastWeekWords) / lastWeekWords) * 1000) / 10.0
                                : (thisWeekWords > 0 ? 100.0 : 0.0);

                // -- 上周正确率 --
                double accuracyWow = 0.0;
                List<ReviewTask> lastWeekTasks = reviewTaskMapper.selectList(new LambdaQueryWrapper<ReviewTask>()
                                .eq(ReviewTask::getUserId, userId)
                                .eq(ReviewTask::getIsCompleted, 1)
                                .ge(ReviewTask::getLastReviewTime, lastWeekStart)
                                .lt(ReviewTask::getLastReviewTime, lastWeekEnd)
                                .isNotNull(ReviewTask::getAccuracyScore));
                if (!lastWeekTasks.isEmpty()) {
                        double lastWeekAcc = 0;
                        for (ReviewTask t : lastWeekTasks) {
                                if (t.getAccuracyScore() != null)
                                        lastWeekAcc += t.getAccuracyScore();
                        }
                        lastWeekAcc = lastWeekAcc / lastWeekTasks.size();
                        accuracyWow = lastWeekAcc > 0
                                        ? Math.round(((accuracy - lastWeekAcc) / lastWeekAcc) * 1000) / 10.0
                                        : (accuracy > 0 ? 100.0 : 0.0);
                }

                Map<String, Object> weekOverWeek = new HashMap<>();
                weekOverWeek.put("practiceChange", practiceChangeWow);
                weekOverWeek.put("accuracyChange", accuracyWow);
                weekOverWeek.put("wordsChange", wordsChangeWow);
                growthComparison.put("weekOverWeek", weekOverWeek);

                // -- 月度对比（也汇总多表） --
                LocalDateTime lastMonthStart = LocalDateTime.now().minusDays(60);
                LocalDateTime lastMonthEnd = LocalDateTime.now().minusDays(30);
                long lastMonthLearningRecords = count(new LambdaQueryWrapper<LearningRecord>()
                                .eq(LearningRecord::getUserId, userId).ge(LearningRecord::getCreateTime, lastMonthStart)
                                .lt(LearningRecord::getCreateTime, lastMonthEnd));
                long lastMonthChatMessages = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getUserId, userId).eq(ChatMessage::getRole, "USER")
                                .ge(ChatMessage::getCreateTime, lastMonthStart)
                                .lt(ChatMessage::getCreateTime, lastMonthEnd));
                long lastMonthSearches = searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                                .eq(SearchHistory::getUserId, userId).ge(SearchHistory::getCreateTime, lastMonthStart)
                                .lt(SearchHistory::getCreateTime, lastMonthEnd));
                long lastMonthReadingNotes = readingNoteMapper
                                .selectCount(new LambdaQueryWrapper<ReadingNote>().eq(ReadingNote::getUserId, userId)
                                                .ge(ReadingNote::getCreateTime, lastMonthStart)
                                                .lt(ReadingNote::getCreateTime, lastMonthEnd));
                long lastMonthActivities = lastMonthLearningRecords + lastMonthChatMessages + lastMonthSearches
                                + lastMonthReadingNotes;

                // 本月活动量
                long thisMonthLearningRecords = count(new LambdaQueryWrapper<LearningRecord>()
                                .eq(LearningRecord::getUserId, userId).ge(LearningRecord::getCreateTime, monthStart));
                long thisMonthChatMessages = chatMessageMapper
                                .selectCount(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getUserId, userId)
                                                .eq(ChatMessage::getRole, "USER")
                                                .ge(ChatMessage::getCreateTime, monthStart));
                long thisMonthSearches = searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                                .eq(SearchHistory::getUserId, userId).ge(SearchHistory::getCreateTime, monthStart));
                long thisMonthReadingNotes = readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>()
                                .eq(ReadingNote::getUserId, userId).ge(ReadingNote::getCreateTime, monthStart));
                long thisMonthActivities = thisMonthLearningRecords + thisMonthChatMessages + thisMonthSearches
                                + thisMonthReadingNotes;

                double practiceChangeMom = lastMonthActivities > 0
                                ? Math.round(((double) (thisMonthActivities - lastMonthActivities)
                                                / lastMonthActivities) * 1000) / 10.0
                                : (thisMonthActivities > 0 ? 100.0 : 0.0);

                long lastMonthWords = wordBookMapper
                                .selectCount(new LambdaQueryWrapper<WordBook>().eq(WordBook::getUserId, userId)
                                                .ge(WordBook::getCreateTime, lastMonthStart)
                                                .lt(WordBook::getCreateTime, lastMonthEnd));
                long thisMonthWords = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                                .eq(WordBook::getUserId, userId).ge(WordBook::getCreateTime, monthStart));
                double wordsChangeMom = lastMonthWords > 0
                                ? Math.round(((double) (thisMonthWords - lastMonthWords) / lastMonthWords) * 1000)
                                                / 10.0
                                : (thisMonthWords > 0 ? 100.0 : 0.0);

                double accuracyMom = 0.0;
                List<ReviewTask> lastMonthTasks = reviewTaskMapper.selectList(new LambdaQueryWrapper<ReviewTask>()
                                .eq(ReviewTask::getUserId, userId)
                                .eq(ReviewTask::getIsCompleted, 1)
                                .ge(ReviewTask::getLastReviewTime, lastMonthStart)
                                .lt(ReviewTask::getLastReviewTime, lastMonthEnd)
                                .isNotNull(ReviewTask::getAccuracyScore));
                if (!lastMonthTasks.isEmpty()) {
                        double lastMonthAcc = 0;
                        for (ReviewTask t : lastMonthTasks) {
                                if (t.getAccuracyScore() != null)
                                        lastMonthAcc += t.getAccuracyScore();
                        }
                        lastMonthAcc = lastMonthAcc / lastMonthTasks.size();
                        accuracyMom = lastMonthAcc > 0
                                        ? Math.round(((accuracy - lastMonthAcc) / lastMonthAcc) * 1000) / 10.0
                                        : (accuracy > 0 ? 100.0 : 0.0);
                }

                Map<String, Object> monthOverMonth = new HashMap<>();
                monthOverMonth.put("practiceChange", practiceChangeMom);
                monthOverMonth.put("accuracyChange", accuracyMom);
                monthOverMonth.put("wordsChange", wordsChangeMom);
                growthComparison.put("monthOverMonth", monthOverMonth);

                result.put("growthComparison", growthComparison);

                // 5. 雷达图数据
                try {
                        Map<String, Object> radarData = getRadarData(userId, "week");
                        result.put("radarData", radarData);
                } catch (Exception e) {
                        log.error("获取雷达图数据失败", e);
                        result.put("radarData", new HashMap<>());
                }

                return result;
        }

        private Map<String, Object> getSubjectBriefStats(Long userId, String subject) {
                long notes = aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                                .eq(AiNote::getUserId, userId).eq(AiNote::getSubject, subject));

                // 英语学科额外加入精读笔记
                if ("english".equals(subject)) {
                        long readingNotes = readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>()
                                        .eq(ReadingNote::getUserId, userId));
                        notes += readingNotes;
                }

                // 学科AI对话数量
                long chatCount = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getUserId, userId).eq(ChatMessage::getRole, "USER")
                                .eq(ChatMessage::getSubject, subject));

                Map<String, Object> data = new HashMap<>();
                data.put("notes", notes);
                data.put("chatCount", chatCount);
                data.put("label", SUBJECT_LABELS.getOrDefault(subject, subject));
                return data;
        }

        private int calculateStreak(Long userId) {
                int streak = 0;
                LocalDate date = LocalDate.now();
                while (true) {
                        LocalDateTime dayStart = date.atStartOfDay();
                        LocalDateTime dayEnd = dayStart.plusDays(1);
                        long count = count(new LambdaQueryWrapper<LearningRecord>()
                                        .eq(LearningRecord::getUserId, userId)
                                        .ge(LearningRecord::getCreateTime, dayStart)
                                        .lt(LearningRecord::getCreateTime, dayEnd));
                        if (count == 0) {
                                count = countActiveFromOtherTables(userId, dayStart, dayEnd);
                        }
                        if (count > 0) {
                                streak++;
                                date = date.minusDays(1);
                        } else {
                                break;
                        }
                }
                return streak;
        }

        private long countActiveFromOtherTables(Long userId, LocalDateTime dayStart, LocalDateTime dayEnd) {
                long count = 0;
                count += chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getUserId, userId)
                                .eq(ChatMessage::getRole, "USER")
                                .ge(ChatMessage::getCreateTime, dayStart)
                                .lt(ChatMessage::getCreateTime, dayEnd));
                count += aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                                .eq(AiNote::getUserId, userId)
                                .ge(AiNote::getCreateTime, dayStart)
                                .lt(AiNote::getCreateTime, dayEnd));
                count += searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                                .eq(SearchHistory::getUserId, userId)
                                .ge(SearchHistory::getCreateTime, dayStart)
                                .lt(SearchHistory::getCreateTime, dayEnd));
                return count;
        }

        private int calculateOverallScore(long totalRecords, long totalWords,
                        long masteredWords, int streak) {
                int score = 0;
                score += Math.min(25, (int) totalRecords);
                score += Math.min(15, (int) masteredWords / 2);
                score += Math.min(10, (int) totalWords / 5);
                score += Math.min(15, streak * 3);
                score += Math.min(20, (int) (totalRecords > 0 ? 15 : 0));
                return Math.min(100, Math.max(0, score));
        }

        @Override
        public Map<String, Object> getRadarData(Long userId, String timeRange) {
                LocalDateTime startTime;
                if ("week".equals(timeRange)) {
                        startTime = LocalDateTime.now().minusDays(7);
                } else if ("month".equals(timeRange)) {
                        startTime = LocalDateTime.now().minusMonths(1);
                } else {
                        startTime = LocalDateTime.now().minusDays(7);
                }

                // 1. 从 AiNote 获取各科笔记数量（按时间范围过滤）
                List<Map<String, Object>> subjectCounts = aiNoteMapper.selectMaps(
                                new LambdaQueryWrapper<AiNote>()
                                                .select(AiNote::getSubject, AiNote::getId)
                                                .eq(AiNote::getUserId, userId)
                                                .ge(AiNote::getCreateTime, startTime)
                                                .isNotNull(AiNote::getSubject)
                                                .ne(AiNote::getSubject, ""));

                Map<String, Long> countsBySubject = subjectCounts.stream()
                                .filter(m -> m.get("subject") != null)
                                .collect(Collectors.groupingBy(
                                                m -> (String) m.get("subject"),
                                                Collectors.counting()));

                // 2. 从 ReadingNote 获取精读笔记数量
                long readingCount = readingNoteMapper.selectCount(
                                new LambdaQueryWrapper<ReadingNote>()
                                                .eq(ReadingNote::getUserId, userId)
                                                .ge(ReadingNote::getCreateTime, startTime));
                if (readingCount > 0) {
                        countsBySubject.put("reading", countsBySubject.getOrDefault("reading", 0L) + readingCount);
                }

                // 3. 从 WordBook 获取词汇数据
                long vocabCount = wordBookMapper.selectCount(
                                new LambdaQueryWrapper<WordBook>()
                                                .eq(WordBook::getUserId, userId)
                                                .ge(WordBook::getCreateTime, startTime));
                if (vocabCount > 0) {
                        countsBySubject.put("vocabulary", countsBySubject.getOrDefault("vocabulary", 0L) + vocabCount);
                }

                // 4. 从 LongSentence 获取语法数据
                long grammarCount = longSentenceMapper.selectCount(
                                new LambdaQueryWrapper<LongSentence>()
                                                .eq(LongSentence::getUserId, userId)
                                                .ge(LongSentence::getCreateTime, startTime));
                if (grammarCount > 0) {
                        countsBySubject.put("grammar", countsBySubject.getOrDefault("grammar", 0L) + grammarCount);
                }

                // 5. 学科AI对话数量
                List<Map<String, Object>> chatCounts = chatMessageMapper.selectMaps(
                                new LambdaQueryWrapper<ChatMessage>()
                                                .select(ChatMessage::getSubject, ChatMessage::getId)
                                                .eq(ChatMessage::getUserId, userId)
                                                .eq(ChatMessage::getRole, "USER")
                                                .ge(ChatMessage::getCreateTime, startTime)
                                                .isNotNull(ChatMessage::getSubject)
                                                .ne(ChatMessage::getSubject, ""));
                Map<String, Long> chatBySubject = chatCounts.stream()
                                .filter(m -> m.get("subject") != null)
                                .collect(Collectors.groupingBy(
                                                m -> (String) m.get("subject"),
                                                Collectors.counting()));
                for (Map.Entry<String, Long> entry : chatBySubject.entrySet()) {
                        countsBySubject.merge(entry.getKey(), entry.getValue(), Long::sum);
                }

                // 6. 转换为前端需要的结构
                List<Map<String, Object>> dimensions = new ArrayList<>();
                double totalScore = 0;
                int dimensionCount = 0;

                for (Map.Entry<String, String> entry : SUBJECT_LABELS.entrySet()) {
                        String key = entry.getKey();
                        String label = entry.getValue();
                        long rawCount = countsBySubject.getOrDefault(key, 0L);

                        int score = (int) (Math.log1p(rawCount) * 10);
                        score = Math.min(score, 100);

                        if (score > 0) {
                                Map<String, Object> dim = new HashMap<>();
                                dim.put("subject", key);
                                dim.put("label", label);
                                dim.put("score", score);
                                dim.put("fullScore", 100);
                                dimensions.add(dim);
                                totalScore += score;
                                dimensionCount++;
                        }
                }

                // 7. 计算综合分
                int overallScore = (dimensionCount > 0) ? (int) (totalScore / dimensionCount) : 0;

                // 8. 组装最终结果
                Map<String, Object> result = new HashMap<>();
                result.put("dimensions", dimensions);
                result.put("overallScore", overallScore);
                result.put("subjectBreakdown", new ArrayList<>());

                return result;
        }

        private int calculateSubjectScore(long notes) {
                if (notes == 0)
                        return 0;
                int base = 0;
                base += Math.min(50, (int) notes * 10);
                return Math.max(0, Math.min(100, base));
        }

        @Override
        public Map<String, Object> getHeatmapData(Long userId) {
                LocalDateTime startDate = LocalDateTime.now().minusDays(89);
                List<LearningRecord> records = list(new LambdaQueryWrapper<LearningRecord>()
                                .eq(LearningRecord::getUserId, userId)
                                .ge(LearningRecord::getCreateTime, startDate)
                                .orderByAsc(LearningRecord::getCreateTime));

                Map<String, Integer> heatmapMap = new LinkedHashMap<>();
                LocalDate today = LocalDate.now();
                for (int i = 89; i >= 0; i--) {
                        String dateKey = today.minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
                        heatmapMap.put(dateKey, 0);
                }

                for (LearningRecord record : records) {
                        if (record.getCreateTime() == null)
                                continue;
                        String dateKey = record.getCreateTime().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
                        if (heatmapMap.containsKey(dateKey)) {
                                heatmapMap.merge(dateKey, 1, Integer::sum);
                        }
                }

                List<ChatMessage> chatMessages = chatMessageMapper.selectList(
                                new LambdaQueryWrapper<ChatMessage>()
                                                .eq(ChatMessage::getUserId, userId)
                                                .eq(ChatMessage::getRole, "USER")
                                                .ge(ChatMessage::getCreateTime, startDate));
                for (ChatMessage msg : chatMessages) {
                        if (msg.getCreateTime() == null)
                                continue;
                        String dateKey = msg.getCreateTime().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
                        if (heatmapMap.containsKey(dateKey)) {
                                heatmapMap.merge(dateKey, 1, Integer::sum);
                        }
                }

                List<SearchHistory> searchHistories = searchHistoryMapper.selectList(
                                new LambdaQueryWrapper<SearchHistory>()
                                                .eq(SearchHistory::getUserId, userId)
                                                .ge(SearchHistory::getCreateTime, startDate));
                for (SearchHistory sh : searchHistories) {
                        if (sh.getCreateTime() == null)
                                continue;
                        String dateKey = sh.getCreateTime().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
                        if (heatmapMap.containsKey(dateKey)) {
                                heatmapMap.merge(dateKey, 1, Integer::sum);
                        }
                }

                List<AiNote> aiNotes = aiNoteMapper.selectList(
                                new LambdaQueryWrapper<AiNote>()
                                                .eq(AiNote::getUserId, userId)
                                                .ge(AiNote::getCreateTime, startDate));
                for (AiNote note : aiNotes) {
                        if (note.getCreateTime() == null)
                                continue;
                        String dateKey = note.getCreateTime().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
                        if (heatmapMap.containsKey(dateKey)) {
                                heatmapMap.merge(dateKey, 1, Integer::sum);
                        }
                }

                Map<String, Object> result = new HashMap<>();
                result.put("heatmap", heatmapMap);

                Map<String, Integer> colorCount = new HashMap<>();
                colorCount.put("level0", 0);
                colorCount.put("level1", 0);
                colorCount.put("level2", 0);
                colorCount.put("level3", 0);
                colorCount.put("level4", 0);
                for (Integer count : heatmapMap.values()) {
                        if (count == 0)
                                colorCount.merge("level0", 1, Integer::sum);
                        else if (count <= 2)
                                colorCount.merge("level1", 1, Integer::sum);
                        else if (count <= 5)
                                colorCount.merge("level2", 1, Integer::sum);
                        else if (count <= 8)
                                colorCount.merge("level3", 1, Integer::sum);
                        else
                                colorCount.merge("level4", 1, Integer::sum);
                }

                Map<String, Object> weeklyTrend = new HashMap<>();
                for (int w = 3; w >= 0; w--) {
                        LocalDateTime weekStart = LocalDateTime.now().minusWeeks(w);
                        LocalDateTime weekEnd = weekStart.plusWeeks(1);
                        long weekCount = records.stream()
                                        .filter(r -> r.getCreateTime().isAfter(weekStart)
                                                        && r.getCreateTime().isBefore(weekEnd))
                                        .count();
                        weekCount += chatMessages.stream()
                                        .filter(m -> m.getCreateTime().isAfter(weekStart)
                                                        && m.getCreateTime().isBefore(weekEnd))
                                        .count();
                        weekCount += searchHistories.stream()
                                        .filter(s -> s.getCreateTime().isAfter(weekStart)
                                                        && s.getCreateTime().isBefore(weekEnd))
                                        .count();
                        weekCount += aiNotes.stream()
                                        .filter(n -> n.getCreateTime().isAfter(weekStart)
                                                        && n.getCreateTime().isBefore(weekEnd))
                                        .count();
                        weeklyTrend.put("week" + (4 - w), weekCount);
                }

                String aiReport = analyticsAIService.generateHeatmapInsight(userId, heatmapMap);

                result.put("colorDistribution", colorCount);
                result.put("weeklyTrend", weeklyTrend);
                result.put("aiReport", aiReport);
                return result;
        }

        @Override
        public List<Map<String, Object>> getKnowledgeTags(Long userId) {
                Map<String, Integer> tagCount = new HashMap<>();

                // 数据源1：ai_note 表的 knowledge_tags（精粹数据，质量更高）
                List<AiNote> notes = aiNoteMapper.selectList(new LambdaQueryWrapper<AiNote>()
                                .eq(AiNote::getUserId, userId)
                                .isNotNull(AiNote::getKnowledgeTags)
                                .orderByDesc(AiNote::getCreateTime)
                                .last("LIMIT 200"));

                for (AiNote note : notes) {
                        if (note.getKnowledgeTags() != null) {
                                try {
                                        JSONArray tags = JSON.parseArray(note.getKnowledgeTags());
                                        for (int i = 0; i < tags.size(); i++) {
                                                String tag = tags.getString(i);
                                                tagCount.merge(tag, 2, Integer::sum); // 笔记权重x2
                                        }
                                } catch (Exception ignored) {
                                }
                        }
                }

                // 数据源2：chat_message 表的 hidden_json（原始数据，覆盖更广）
                List<ChatMessage> chatMessages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getUserId, userId)
                                .eq(ChatMessage::getRole, "ASSISTANT")
                                .isNotNull(ChatMessage::getHiddenJson)
                                .ne(ChatMessage::getHiddenJson, "")
                                .orderByDesc(ChatMessage::getCreateTime)
                                .last("LIMIT 300"));

                for (ChatMessage msg : chatMessages) {
                        if (msg.getHiddenJson() != null && !msg.getHiddenJson().isEmpty()) {
                                try {
                                        Map<String, Object> parsed = JSON.parseObject(msg.getHiddenJson(), Map.class);
                                        // 提取知识点标签
                                        extractTagsFromHiddenJson(parsed, tagCount);
                                } catch (Exception ignored) {
                                }
                        }
                }

                return tagCount.entrySet().stream()
                                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                .limit(30)
                                .map(entry -> {
                                        Map<String, Object> tag = new HashMap<>();
                                        tag.put("name", entry.getKey());
                                        tag.put("count", entry.getValue());
                                        // 计算标签大小和透明度用于前端词云展示
                                        int maxCount = tagCount.values().stream().max(Integer::compare).orElse(1);
                                        double ratio = (double) entry.getValue() / maxCount;
                                        tag.put("size", (int) (24 + ratio * 32)); // 24-56rpx
                                        tag.put("opacity", 0.5 + ratio * 0.5); // 0.5-1.0
                                        return tag;
                                })
                                .collect(Collectors.toList());
        }

        /**
         * 从hidden_json中提取知识点标签
         */
        private void extractTagsFromHiddenJson(Map<String, Object> parsed, Map<String, Integer> tagCount) {
                // 提取 topic 字段
                Object topic = parsed.get("topic");
                if (topic instanceof String && !((String) topic).isEmpty()) {
                        tagCount.merge((String) topic, 1, Integer::sum);
                }

                // 提取 knowledgePoints 数组
                Object knowledgePoints = parsed.get("knowledgePoints");
                if (knowledgePoints instanceof List) {
                        for (Object kp : (List<?>) knowledgePoints) {
                                if (kp instanceof String && !((String) kp).isEmpty()) {
                                        tagCount.merge((String) kp, 1, Integer::sum);
                                } else if (kp instanceof Map) {
                                        Object name = ((Map<?, ?>) kp).get("name");
                                        if (name instanceof String && !((String) name).isEmpty()) {
                                                tagCount.merge((String) name, 1, Integer::sum);
                                        }
                                }
                        }
                }

                // 提取 tags 数组
                Object tags = parsed.get("tags");
                if (tags instanceof List) {
                        for (Object t : (List<?>) tags) {
                                if (t instanceof String && !((String) t).isEmpty()) {
                                        tagCount.merge((String) t, 1, Integer::sum);
                                }
                        }
                }

                // 提取 coreConcepts 数组
                Object coreConcepts = parsed.get("coreConcepts");
                if (coreConcepts instanceof List) {
                        for (Object cc : (List<?>) coreConcepts) {
                                if (cc instanceof String && !((String) cc).isEmpty()) {
                                        tagCount.merge((String) cc, 1, Integer::sum);
                                }
                        }
                }

                // 提取 subject 字段作为学科标签
                Object subject = parsed.get("subject");
                if (subject instanceof String && !((String) subject).isEmpty()) {
                        // 学科标签不加入高频考点统计
                }
        }

        @Override
        public Map<String, Object> getEnglishStats(Long userId) {
                Map<String, Object> stats = new HashMap<>();
                LocalDateTime todayStart = LocalDate.now().atStartOfDay();

                // ==================== 词汇量统计 ====================
                long wordCount = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                                .eq(WordBook::getUserId, userId));
                long masteredWords = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                                .eq(WordBook::getUserId, userId)
                                .ge(WordBook::getMasteryLevel, 2));
                long todayWords = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                                .eq(WordBook::getUserId, userId)
                                .ge(WordBook::getCreateTime, todayStart));

                // 生词本按分类统计：优先使用迁移后的 bookId，兼容旧 bookName。
                List<WordBook> allWordBooks = wordBookMapper.selectList(new LambdaQueryWrapper<WordBook>()
                                .eq(WordBook::getUserId, userId));
                Map<String, Long> wordBookByCategory = allWordBooks.stream()
                                .collect(Collectors.groupingBy(w -> {
                                        if (w.getBookId() != null) {
                                                return "category:" + w.getBookId();
                                        }
                                        return w.getBookName() != null && !w.getBookName().isEmpty()
                                                        ? w.getBookName()
                                                        : "默认生词本";
                                }, Collectors.counting()));

                stats.put("wordCount", wordCount);
                stats.put("masteredWords", masteredWords);
                stats.put("todayWords", todayWords);
                stats.put("totalVocabulary", wordCount);
                stats.put("wordBookCount", wordBookByCategory.size());
                stats.put("wordBookByCategory", wordBookByCategory);

                // ==================== 笔记数量（合并 AI笔记 + 精读笔记） ====================
                long englishAiNotes = aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                                .eq(AiNote::getUserId, userId).eq(AiNote::getSubject, "english"));
                long readingNotes = readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>()
                                .eq(ReadingNote::getUserId, userId));
                long totalNotes = englishAiNotes + readingNotes;
                stats.put("noteCount", totalNotes);
                stats.put("englishAiNotes", englishAiNotes);
                stats.put("readingNotes", readingNotes);

                // ==================== 错题数量（ReviewTask 中未掌握的） ====================
                long errorCount = reviewTaskMapper.selectCount(new LambdaQueryWrapper<ReviewTask>()
                                .eq(ReviewTask::getUserId, userId)
                                .eq(ReviewTask::getSubject, "english")
                                .lt(ReviewTask::getMasteryLevel, 0.6));
                long totalReviewTasks = reviewTaskMapper.selectCount(new LambdaQueryWrapper<ReviewTask>()
                                .eq(ReviewTask::getUserId, userId)
                                .eq(ReviewTask::getSubject, "english"));
                stats.put("errorCount", errorCount);
                stats.put("totalReviewTasks", totalReviewTasks);

                // ==================== 精读数量（去重 documentId） ====================
                List<ReadingNote> allReadingNotes = readingNoteMapper.selectList(new LambdaQueryWrapper<ReadingNote>()
                                .eq(ReadingNote::getUserId, userId)
                                .isNotNull(ReadingNote::getDocumentId));
                long readingCount = allReadingNotes.stream()
                                .map(ReadingNote::getDocumentId)
                                .filter(id -> id != null && id > 0)
                                .distinct()
                                .count();
                stats.put("readingCount", readingCount);

                // ==================== 长难句 ====================
                long sentenceCount = longSentenceMapper.selectCount(new LambdaQueryWrapper<LongSentence>()
                                .eq(LongSentence::getUserId, userId));
                stats.put("sentenceCount", sentenceCount);
                stats.put("sentencesAnalyzed", sentenceCount);

                // ==================== 查词量 ====================
                long searchCount = searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                                .eq(SearchHistory::getUserId, userId));
                long todaySearches = searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                                .eq(SearchHistory::getUserId, userId)
                                .ge(SearchHistory::getCreateTime, todayStart));
                stats.put("searchCount", searchCount);
                stats.put("todaySearches", todaySearches);

                // ==================== 阅读时间（只汇总 reading 来源的 learning_record）
                // ====================
                long totalReadingSeconds = 0;
                long todayReadingSeconds = 0;
                List<LearningRecord> allDurationRecords = list(new LambdaQueryWrapper<LearningRecord>()
                                .eq(LearningRecord::getUserId, userId)
                                .and(w -> w.eq(LearningRecord::getRecordType, "reading")
                                                .or().eq(LearningRecord::getSourceType, "reading"))
                                .isNotNull(LearningRecord::getDuration));
                for (LearningRecord r : allDurationRecords) {
                        if (r.getDuration() != null)
                                totalReadingSeconds += r.getDuration();
                }
                List<LearningRecord> todayDurationRecords = list(new LambdaQueryWrapper<LearningRecord>()
                                .eq(LearningRecord::getUserId, userId)
                                .and(w -> w.eq(LearningRecord::getRecordType, "reading")
                                                .or().eq(LearningRecord::getSourceType, "reading"))
                                .ge(LearningRecord::getCreateTime, todayStart)
                                .isNotNull(LearningRecord::getDuration));
                for (LearningRecord r : todayDurationRecords) {
                        if (r.getDuration() != null)
                                todayReadingSeconds += r.getDuration();
                }
                stats.put("readingMinutes", totalReadingSeconds / 60);
                stats.put("todayReadingMinutes", todayReadingSeconds / 60);

                // ==================== 掌握程度 ====================
                double masteryPercent = wordCount > 0 ? Math.round((double) masteredWords / wordCount * 1000) / 10.0
                                : 0;
                stats.put("masteryPercent", masteryPercent);
                stats.put("vocabScore", Math.min(100, (int) wordCount));

                // ==================== 估算阅读速度WPM ====================
                int estimatedWPM = 0;
                if (readingCount > 0 && searchCount > 0) {
                        double avgLookupsPerArticle = (double) searchCount / readingCount;
                        if (avgLookupsPerArticle <= 3)
                                estimatedWPM = 180;
                        else if (avgLookupsPerArticle <= 6)
                                estimatedWPM = 150;
                        else if (avgLookupsPerArticle <= 10)
                                estimatedWPM = 120;
                        else
                                estimatedWPM = 90;
                }
                stats.put("estimatedWPM", estimatedWPM);

                // ==================== 阅读理解正确率（基于review_task） ====================
                long correctReviews = reviewTaskMapper.selectCount(new LambdaQueryWrapper<ReviewTask>()
                                .eq(ReviewTask::getUserId, userId)
                                .eq(ReviewTask::getSubject, "english")
                                .ge(ReviewTask::getMasteryLevel, 0.6));
                double comprehensionAccuracy = totalReviewTasks > 0
                                ? Math.round((double) correctReviews / totalReviewTasks * 1000) / 10.0
                                : 0;
                stats.put("comprehensionAccuracy", comprehensionAccuracy);
                stats.put("correctReviews", correctReviews);

                // ==================== 核心考研词汇数量 ====================
                long coreVocabCount = allWordBooks.stream()
                                .filter(w -> w.getBookName() != null && w.getBookName().contains("考研"))
                                .count();
                stats.put("coreVocabCount", coreVocabCount);

                // ==================== 长难词数量（单词长度>=8） ====================
                long longDifficultWordCount = allWordBooks.stream()
                                .filter(w -> w.getWord() != null && w.getWord().length() >= 8)
                                .count();
                stats.put("longDifficultWordCount", longDifficultWordCount);

                // ==================== 英语AI对话数量 ====================
                long englishChats = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getUserId, userId)
                                .eq(ChatMessage::getRole, "USER")
                                .eq(ChatMessage::getSubject, "english"));
                stats.put("englishChats", englishChats);

                // ==================== 近4周趋势 ====================
                List<Map<String, Object>> weeklyTrend = new ArrayList<>();
                for (int i = 3; i >= 0; i--) {
                        LocalDateTime wEnd = LocalDate.now().minusWeeks(i).plusDays(1).atStartOfDay();
                        LocalDateTime wStart = wEnd.minusDays(7);
                        long wWords = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                                        .eq(WordBook::getUserId, userId)
                                        .ge(WordBook::getCreateTime, wStart)
                                        .lt(WordBook::getCreateTime, wEnd));
                        long wSearches = searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                                        .eq(SearchHistory::getUserId, userId)
                                        .ge(SearchHistory::getCreateTime, wStart)
                                        .lt(SearchHistory::getCreateTime, wEnd));
                        long wNotes = aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                                        .eq(AiNote::getUserId, userId).eq(AiNote::getSubject, "english")
                                        .ge(AiNote::getCreateTime, wStart)
                                        .lt(AiNote::getCreateTime, wEnd));
                        Map<String, Object> weekData = new HashMap<>();
                        weekData.put("label", "第" + (4 - i) + "周");
                        weekData.put("words", wWords);
                        weekData.put("searches", wSearches);
                        weekData.put("notes", wNotes);
                        weekData.put("count", wWords + wSearches + wNotes);
                        weeklyTrend.add(weekData);
                }
                stats.put("weeklyTrend", weeklyTrend);

                try {
                        Map<String, Object> aiInsight = analyticsAIService.generateEnglishInsight(userId);
                        stats.put("aiInsight", aiInsight);
                } catch (Exception e) {
                        log.warn("英语AI分析生成失败: {}", e.getMessage());
                }

                return stats;
        }

        @Override
        public Map<String, Object> getReadingAnalysis(Long userId) {
                Map<String, Object> analysis = new HashMap<>();
                LocalDateTime monthStart = LocalDateTime.now().minusDays(30);

                // ==================== 基础统计 ====================
                long readingNotes = readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>()
                                .eq(ReadingNote::getUserId, userId));
                List<WordBook> allWordBookRows = wordBookMapper.selectList(new LambdaQueryWrapper<WordBook>()
                                .eq(WordBook::getUserId, userId));
                long wordCount = allWordBookRows.stream()
                                .map(WordBook::getWord)
                                .filter(word -> word != null && !word.isEmpty())
                                .map(String::toLowerCase)
                                .distinct()
                                .count();
                long sentenceCount = longSentenceMapper.selectCount(new LambdaQueryWrapper<LongSentence>()
                                .eq(LongSentence::getUserId, userId));

                // 统计已读文章数：优先使用 reading_session，兜底用 reading_note + learning_record
                List<ReadingSession> allReadingSessions = readingSessionMapper
                                .selectList(new LambdaQueryWrapper<ReadingSession>()
                                                .eq(ReadingSession::getUserId, userId));
                List<ReadingNote> allNotes = readingNoteMapper.selectList(new LambdaQueryWrapper<ReadingNote>()
                                .eq(ReadingNote::getUserId, userId)
                                .isNotNull(ReadingNote::getDocumentId));
                List<LearningRecord> readingRecords = list(new LambdaQueryWrapper<LearningRecord>()
                                .eq(LearningRecord::getUserId, userId)
                                .and(w -> w.eq(LearningRecord::getRecordType, "reading")
                                                .or().eq(LearningRecord::getSourceType, "reading")));
                // 优先用 reading_session 去重 documentId
                long readingCount = allReadingSessions.stream()
                                .map(ReadingSession::getDocumentId)
                                .filter(id -> id != null && id > 0)
                                .distinct()
                                .count();
                // 兜底：reading_note 去重 documentId
                long noteDocCount = allNotes.stream()
                                .map(ReadingNote::getDocumentId)
                                .filter(id -> id != null && id > 0)
                                .distinct()
                                .count();
                readingCount = Math.max(readingCount, Math.max(noteDocCount, readingRecords.size()));

                Map<String, Object> overview = new HashMap<>();
                overview.put("readingCount", readingCount);
                overview.put("totalNotes", readingNotes);
                long wordBookCategoryCount = allWordBookRows.stream()
                                .map(w -> w.getBookId() != null
                                                ? "category:" + w.getBookId()
                                                : (w.getBookName() != null && !w.getBookName().isEmpty()
                                                                ? w.getBookName()
                                                                : "默认生词本"))
                                .distinct()
                                .count();
                overview.put("wordBookCount", wordBookCategoryCount);
                overview.put("wordCount", wordCount);
                overview.put("sentenceCount", sentenceCount);
                // 今日新增
                LocalDate today = LocalDate.now();
                long todayNotes = allNotes.stream()
                                .filter(n -> {
                                        LocalDateTime t = readingNoteTime(n);
                                        return t != null && t.toLocalDate().equals(today);
                                })
                                .count();
                long todayWords = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                                .eq(WordBook::getUserId, userId)
                                .ge(WordBook::getCreateTime, today.atStartOfDay()));
                long todaySentences = longSentenceMapper.selectCount(new LambdaQueryWrapper<LongSentence>()
                                .eq(LongSentence::getUserId, userId)
                                .ge(LongSentence::getCreateTime, today.atStartOfDay()));
                overview.put("todayNotes", todayNotes);
                overview.put("todayWords", todayWords);
                overview.put("todaySentences", todaySentences);
                analysis.put("overview", overview);

                // ==================== 笔记类型分布 ====================
                Map<String, Long> noteTypeDist = allNotes.stream()
                                .filter(n -> n.getNoteType() != null && !n.getNoteType().isEmpty())
                                .collect(Collectors.groupingBy(ReadingNote::getNoteType, Collectors.counting()));
                analysis.put("noteTypeDistribution", noteTypeDist);

                // ==================== 高亮颜色分布 ====================
                Map<String, Long> highlightColorDist = allNotes.stream()
                                .filter(n -> n.getHighlightColor() != null && !n.getHighlightColor().isEmpty())
                                .collect(Collectors.groupingBy(ReadingNote::getHighlightColor, Collectors.counting()));
                analysis.put("highlightColorDistribution", highlightColorDist);

                // ==================== 高频知识点标签 ====================
                // 从精读笔记的知识点提取
                List<Map<String, Object>> knowledgeTags = allNotes.stream()
                                .filter(n -> n.getKnowledgePoints() != null && !n.getKnowledgePoints().isEmpty())
                                .flatMap(n -> Arrays.stream(n.getKnowledgePoints().split("[,，;；]")))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
                                .entrySet().stream()
                                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                                .limit(15)
                                .map(e -> {
                                        Map<String, Object> tag = new HashMap<>();
                                        tag.put("name", e.getKey());
                                        tag.put("count", e.getValue());
                                        return tag;
                                })
                                .collect(Collectors.toList());

                // 从长难句的grammarTags中提取语法标签
                try {
                        List<LongSentence> userSentences = longSentenceMapper.selectList(
                                        new LambdaQueryWrapper<LongSentence>()
                                                        .eq(LongSentence::getUserId, userId)
                                                        .isNotNull(LongSentence::getGrammarTags));
                        Map<String, Long> grammarTagCounts = new HashMap<>();
                        for (LongSentence ls : userSentences) {
                                if (ls.getGrammarTags() != null && !ls.getGrammarTags().isEmpty()) {
                                        try {
                                                JSONArray tagsArr = JSON.parseArray(ls.getGrammarTags());
                                                if (tagsArr != null) {
                                                        for (int i = 0; i < tagsArr.size(); i++) {
                                                                String tag = tagsArr.getString(i);
                                                                if (tag != null && !tag.trim().isEmpty()) {
                                                                        grammarTagCounts.merge(tag.trim(), 1L,
                                                                                        Long::sum);
                                                                }
                                                        }
                                                }
                                        } catch (Exception e) {
                                                // grammarTags可能不是JSON数组格式，尝试逗号分隔
                                                Arrays.stream(ls.getGrammarTags().split("[,，;；\\[\\]\"']"))
                                                                .map(String::trim)
                                                                .filter(s -> !s.isEmpty() && !s.equals("null"))
                                                                .forEach(tag -> grammarTagCounts.merge(tag, 1L,
                                                                                Long::sum));
                                        }
                                }
                        }
                        // 合并到knowledgeTags中
                        for (Map.Entry<String, Long> entry : grammarTagCounts.entrySet()) {
                                Optional<Map<String, Object>> existing = knowledgeTags.stream()
                                                .filter(t -> entry.getKey().equals(t.get("name")))
                                                .findFirst();
                                if (existing.isPresent()) {
                                        existing.get().put("count",
                                                        (Long) existing.get().get("count") + entry.getValue());
                                } else {
                                        Map<String, Object> tag = new HashMap<>();
                                        tag.put("name", entry.getKey());
                                        tag.put("count", entry.getValue());
                                        knowledgeTags.add(tag);
                                }
                        }
                        // 重新排序并限制数量
                        knowledgeTags = knowledgeTags.stream()
                                        .sorted((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")))
                                        .limit(15)
                                        .collect(Collectors.toList());
                } catch (Exception e) {
                        log.warn("聚合长难句语法标签失败: {}", e.getMessage());
                }

                analysis.put("knowledgeTags", knowledgeTags);

                // ==================== 近4周趋势 ====================
                List<Map<String, Object>> weeklyTrend = new ArrayList<>();
                DateTimeFormatter weekFmt = DateTimeFormatter.ofPattern("MM/dd");
                for (int i = 3; i >= 0; i--) {
                        LocalDate weekEnd = LocalDate.now().minusWeeks(i);
                        LocalDate weekStart = weekEnd.minusDays(6);
                        long weekNotes = allNotes.stream()
                                        .filter(n -> {
                                                LocalDateTime t = readingNoteTime(n);
                                                return t != null && !t.toLocalDate().isBefore(weekStart)
                                                                && t.toLocalDate().isBefore(weekEnd.plusDays(1));
                                        })
                                        .count();
                        long weekReadingSessions = readingRecords.stream()
                                        .filter(r -> {
                                                LocalDateTime t = learningRecordTime(r);
                                                return t != null && !t.toLocalDate().isBefore(weekStart)
                                                                && t.toLocalDate().isBefore(weekEnd.plusDays(1));
                                        })
                                        .count();
                        long weekDocs = allNotes.stream()
                                        .filter(n -> {
                                                LocalDateTime t = readingNoteTime(n);
                                                return t != null && !t.toLocalDate().isBefore(weekStart)
                                                                && t.toLocalDate().isBefore(weekEnd.plusDays(1));
                                        })
                                        .map(ReadingNote::getDocumentId)
                                        .distinct()
                                        .count();
                        // 优先用 reading_session 统计该周阅读文章数
                        long weekSessionDocs = allReadingSessions.stream()
                                        .filter(s -> {
                                                LocalDateTime t = s.getStartTime();
                                                return t != null && !t.toLocalDate().isBefore(weekStart)
                                                                && t.toLocalDate().isBefore(weekEnd.plusDays(1));
                                        })
                                        .map(ReadingSession::getDocumentId)
                                        .filter(id -> id != null && id > 0)
                                        .distinct()
                                        .count();
                        long weekSessionCount = allReadingSessions.stream()
                                        .filter(s -> {
                                                LocalDateTime t = s.getStartTime();
                                                return t != null && !t.toLocalDate().isBefore(weekStart)
                                                                && t.toLocalDate().isBefore(weekEnd.plusDays(1));
                                        })
                                        .count();
                        Map<String, Object> week = new HashMap<>();
                        week.put("label", weekStart.format(weekFmt) + "-" + weekEnd.format(weekFmt));
                        week.put("count", weekNotes + weekReadingSessions + weekSessionCount);
                        week.put("notes", weekNotes);
                        // articles 优先用 reading_session，兜底用 note 去重 + learning_record
                        week.put("articles", Math.max(Math.max(weekSessionDocs, weekDocs), weekReadingSessions));
                        weeklyTrend.add(week);
                }
                analysis.put("weeklyTrend", weeklyTrend);

                // ==================== 阅读时段分布 ====================
                Map<String, Long> timeDist = new HashMap<>();
                timeDist.put("morning", 0L); // 6-12
                timeDist.put("afternoon", 0L); // 12-18
                timeDist.put("evening", 0L); // 18-24
                timeDist.put("night", 0L); // 0-6
                // 统计笔记创建时段
                for (ReadingNote n : allNotes) {
                        LocalDateTime noteTime = readingNoteTime(n);
                        if (noteTime == null)
                                continue;
                        int hour = noteTime.getHour();
                        if (hour >= 6 && hour < 12)
                                timeDist.merge("morning", 1L, Long::sum);
                        else if (hour >= 12 && hour < 18)
                                timeDist.merge("afternoon", 1L, Long::sum);
                        else if (hour >= 18 && hour < 24)
                                timeDist.merge("evening", 1L, Long::sum);
                        else
                                timeDist.merge("night", 1L, Long::sum);
                }
                // 同时统计 learning_record 中阅读记录的时段
                for (LearningRecord r : readingRecords) {
                        LocalDateTime recordTime = learningRecordTime(r);
                        if (recordTime == null)
                                continue;
                        int hour = recordTime.getHour();
                        if (hour >= 6 && hour < 12)
                                timeDist.merge("morning", 1L, Long::sum);
                        else if (hour >= 12 && hour < 18)
                                timeDist.merge("afternoon", 1L, Long::sum);
                        else if (hour >= 18 && hour < 24)
                                timeDist.merge("evening", 1L, Long::sum);
                        else
                                timeDist.merge("night", 1L, Long::sum);
                }
                // 优先统计 reading_session 的阅读时段
                for (ReadingSession s : allReadingSessions) {
                        LocalDateTime sessionTime = s.getStartTime();
                        if (sessionTime == null)
                                continue;
                        int hour = sessionTime.getHour();
                        if (hour >= 6 && hour < 12)
                                timeDist.merge("morning", 1L, Long::sum);
                        else if (hour >= 12 && hour < 18)
                                timeDist.merge("afternoon", 1L, Long::sum);
                        else if (hour >= 18 && hour < 24)
                                timeDist.merge("evening", 1L, Long::sum);
                        else
                                timeDist.merge("night", 1L, Long::sum);
                }
                analysis.put("noteTimeDistribution", timeDist);

                // ==================== 文章维度分析 ====================
                // 按 documentId 分组统计每篇文章的笔记数
                Map<Long, Long> docNoteCount = allNotes.stream()
                                .filter(n -> n.getDocumentId() != null && n.getDocumentId() > 0)
                                .collect(Collectors.groupingBy(ReadingNote::getDocumentId, Collectors.counting()));

                // 查询文章文件名
                Map<Long, String> docIdToName = new HashMap<>();
                if (!docNoteCount.isEmpty()) {
                        List<Long> docIds = new ArrayList<>(docNoteCount.keySet());
                        List<PdfDocument> documents = pdfDocumentMapper.selectList(new LambdaQueryWrapper<PdfDocument>()
                                        .in(PdfDocument::getId, docIds)
                                        .eq(PdfDocument::getUserId, userId));
                        for (PdfDocument doc : documents) {
                                docIdToName.put(doc.getId(), doc.getFileName());
                        }
                }

                List<Map<String, Object>> articleAnalysis = docNoteCount.entrySet().stream()
                                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                                .limit(10)
                                .map(entry -> {
                                        Map<String, Object> item = new HashMap<>();
                                        item.put("documentId", entry.getKey());
                                        item.put("noteCount", entry.getValue());
                                        item.put("fileName", docIdToName.getOrDefault(entry.getKey(), "未知文章"));
                                        return item;
                                })
                                .collect(Collectors.toList());
                analysis.put("articleAnalysis", articleAnalysis);

                // ==================== 词汇维度分析 ====================
                // 精读中查询的词汇：search_history type=word 或来自reading的记录
                List<SearchHistory> readingSearches = searchHistoryMapper.selectList(
                                new LambdaQueryWrapper<SearchHistory>()
                                                .eq(SearchHistory::getUserId, userId)
                                                .ge(SearchHistory::getCreateTime, monthStart)
                                                .orderByDesc(SearchHistory::getCreateTime)
                                                .last("LIMIT 200"));
                long wordsQueriedInReading = readingSearches.stream()
                                .filter(s -> s.getQueryType() != null && s.getQueryType().contains("WORD"))
                                .count();

                // 生词本中来源于精读的词汇
                Set<String> searchedWords = readingSearches.stream()
                                .filter(s -> s.getKeyword() != null)
                                .map(s -> s.getKeyword().toLowerCase())
                                .collect(Collectors.toSet());

                List<WordBook> allWordBooks = allWordBookRows;
                long fromReadingCount = allWordBooks.stream()
                                .filter(wb -> searchedWords.contains(wb.getWord().toLowerCase()))
                                .count();

                List<String> topQueriedWords = readingSearches.stream()
                                .filter(s -> s.getQueryType() != null && s.getQueryType().contains("WORD")
                                                && s.getKeyword() != null && !s.getKeyword().isEmpty())
                                .map(SearchHistory::getKeyword)
                                .collect(Collectors.groupingBy(w -> w, Collectors.counting()))
                                .entrySet().stream()
                                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                                .limit(10)
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList());

                Map<String, Object> vocabulary = new HashMap<>();
                vocabulary.put("totalQueried", readingSearches.size());
                vocabulary.put("wordsQueriedInReading", wordsQueriedInReading);
                vocabulary.put("wordsInWordBook", allWordBooks.size());
                vocabulary.put("fromReadingCount", fromReadingCount);
                vocabulary.put("topQueriedWords", topQueriedWords);
                analysis.put("vocabularyInReading", vocabulary);

                // ==================== 长难句分析 ====================
                Map<String, Long> sentenceDiffDist = longSentenceMapper.selectList(
                                new LambdaQueryWrapper<LongSentence>()
                                                .eq(LongSentence::getUserId, userId)
                                                .isNotNull(LongSentence::getDifficulty))
                                .stream()
                                .filter(s -> s.getDifficulty() != null && !s.getDifficulty().isEmpty())
                                .collect(Collectors.groupingBy(LongSentence::getDifficulty, Collectors.counting()));
                analysis.put("sentenceDifficultyDistribution", sentenceDiffDist);

                // ==================== AI洞察 ====================
                try {
                        Map<String, Object> aiInsight = analyticsAIService.generateReadingInsight(userId, analysis);
                        analysis.put("aiInsight", aiInsight);
                } catch (Exception e) {
                        log.warn("精读AI分析生成失败: {}", e.getMessage());
                }

                return analysis;
        }

        private LocalDateTime readingNoteTime(ReadingNote note) {
                if (note == null) {
                        return null;
                }
                return note.getCreateTime() != null ? note.getCreateTime() : note.getUpdateTime();
        }

        private LocalDateTime learningRecordTime(LearningRecord record) {
                if (record == null) {
                        return null;
                }
                return record.getCreateTime() != null ? record.getCreateTime() : record.getUpdateTime();
        }

        @Override
        public Map<String, Object> getAIAnalysis(Long userId) {
                return analyticsAIService.generateDiagnosisReport(userId);
        }

        @Override
        public Map<String, Object> getSubjectAnalysis(Long userId, String subject) {
                LocalDateTime weekStart = LocalDateTime.now().minusDays(7);
                LocalDateTime todayStart = LocalDate.now().atStartOfDay();

                Map<String, Object> result = new HashMap<>();

                // 1. AI笔记统计
                long totalNotes = aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                                .eq(AiNote::getUserId, userId)
                                .eq(AiNote::getSubject, subject));

                long todayNotes = aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                                .eq(AiNote::getUserId, userId)
                                .eq(AiNote::getSubject, subject)
                                .ge(AiNote::getCreateTime, todayStart));

                long weekNotes = aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                                .eq(AiNote::getUserId, userId)
                                .eq(AiNote::getSubject, subject)
                                .ge(AiNote::getCreateTime, weekStart));

                // 2. 英语学科额外加入精读笔记
                long totalReadingNotes = 0;
                long todayReadingNotes = 0;
                long weekReadingNotes = 0;
                if ("english".equals(subject)) {
                        totalReadingNotes = readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>()
                                        .eq(ReadingNote::getUserId, userId));
                        todayReadingNotes = readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>()
                                        .eq(ReadingNote::getUserId, userId)
                                        .ge(ReadingNote::getCreateTime, todayStart));
                        weekReadingNotes = readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>()
                                        .eq(ReadingNote::getUserId, userId)
                                        .ge(ReadingNote::getCreateTime, weekStart));
                }
                long totalAllNotes = totalNotes + totalReadingNotes;
                long todayAllNotes = todayNotes + todayReadingNotes;
                long weekAllNotes = weekNotes + weekReadingNotes;

                // 3. 学科AI对话数量（chat_message 带 subject 字段）
                long subjectChatCount = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getUserId, userId)
                                .eq(ChatMessage::getRole, "USER")
                                .eq(ChatMessage::getSubject, subject)
                                .ge(ChatMessage::getCreateTime, weekStart));

                // 4. 错题数量（review_task 中未掌握的）
                long errorCount = reviewTaskMapper.selectCount(new LambdaQueryWrapper<ReviewTask>()
                                .eq(ReviewTask::getUserId, userId)
                                .eq(ReviewTask::getIsCompleted, 0));

                // 5. 知识标签统计
                Map<String, Integer> knowledgeTagCount = new HashMap<>();
                List<AiNote> notes = aiNoteMapper.selectList(new LambdaQueryWrapper<AiNote>()
                                .eq(AiNote::getUserId, userId)
                                .eq(AiNote::getSubject, subject)
                                .isNotNull(AiNote::getKnowledgeTags)
                                .orderByDesc(AiNote::getCreateTime)
                                .last("LIMIT 50"));

                for (AiNote note : notes) {
                        if (note.getKnowledgeTags() != null) {
                                try {
                                        JSONArray tags = JSON.parseArray(note.getKnowledgeTags());
                                        for (int i = 0; i < tags.size(); i++) {
                                                String tag = tags.getString(i);
                                                knowledgeTagCount.merge(tag, 1, Integer::sum);
                                        }
                                } catch (Exception ignored) {
                                }
                        }
                }

                List<Map<String, Object>> topTags = knowledgeTagCount.entrySet().stream()
                                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                .limit(10)
                                .map(entry -> {
                                        Map<String, Object> tag = new HashMap<>();
                                        tag.put("name", entry.getKey());
                                        tag.put("count", entry.getValue());
                                        return tag;
                                })
                                .collect(Collectors.toList());

                List<AiNote> recentNotes = aiNoteMapper.selectList(new LambdaQueryWrapper<AiNote>()
                                .eq(AiNote::getUserId, userId)
                                .eq(AiNote::getSubject, subject)
                                .orderByDesc(AiNote::getCreateTime)
                                .last("LIMIT 5"));

                List<Map<String, Object>> recentList = recentNotes.stream().map(note -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("id", note.getId());
                        item.put("questionText", note.getQuestionText() != null && note.getQuestionText().length() > 60
                                        ? note.getQuestionText().substring(0, 60) + "..."
                                        : note.getQuestionText());
                        item.put("keyPoints", note.getKeyPoints());
                        item.put("createTime", note.getCreateTime() != null
                                        ? note.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE)
                                        : "");
                        return item;
                }).collect(Collectors.toList());

                List<Map<String, Object>> weakPoints = knowledgeTagCount.entrySet().stream()
                                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                .limit(5)
                                .map(entry -> {
                                        Map<String, Object> wp = new HashMap<>();
                                        wp.put("name", entry.getKey());
                                        wp.put("noteCount", entry.getValue());
                                        return wp;
                                })
                                .collect(Collectors.toList());

                int masteryScore = calculateSubjectMasteryScore(totalAllNotes, weekAllNotes);

                result.put("subject", subject);
                result.put("totalNotes", totalAllNotes);
                result.put("todayNotes", todayAllNotes);
                result.put("weekNotes", weekAllNotes);
                result.put("chatCount", subjectChatCount);
                result.put("errorCount", errorCount);
                result.put("masteryScore", masteryScore);
                result.put("topKnowledgeTags", topTags);
                result.put("recentNotes", recentList);
                result.put("weakPoints", weakPoints);

                Map<String, String> labelMap = new HashMap<>();
                labelMap.put("math", "数学");
                labelMap.put("ds", "数据结构");
                labelMap.put("co", "计算机组成原理");
                labelMap.put("os", "操作系统");
                labelMap.put("cn", "计算机网络");
                labelMap.put("english", "英语");
                result.put("subjectLabel", labelMap.getOrDefault(subject, subject));

                if ("math".equals(subject)) {
                        result.put("trapWarningStats", getMathTrapStats(userId));
                }

                try {
                        Map<String, Object> aiInsight = analyticsAIService.generateSubjectInsight(userId, subject);
                        result.put("aiInsight", aiInsight);
                } catch (Exception e) {
                        log.warn("学科AI分析生成失败: {}", e.getMessage());
                }

                return result;
        }

        private List<Map<String, Object>> getMathTrapStats(Long userId) {
                List<AiNote> mathNotes = aiNoteMapper.selectList(new LambdaQueryWrapper<AiNote>()
                                .eq(AiNote::getUserId, userId)
                                .eq(AiNote::getSubject, "math")
                                .isNotNull(AiNote::getKeyPoints)
                                .orderByDesc(AiNote::getCreateTime)
                                .last("LIMIT 30"));

                Map<String, Integer> trapCount = new HashMap<>();
                for (AiNote note : mathNotes) {
                        if (note.getKeyPoints() != null && note.getKeyPoints().contains("易错")) {
                                trapCount.merge("易错点", 1, Integer::sum);
                        }
                }
                List<Map<String, Object>> result = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : trapCount.entrySet()) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("type", entry.getKey());
                        item.put("count", entry.getValue());
                        result.add(item);
                }
                return result;
        }

        private int calculateSubjectMasteryScore(long totalNotes, long weekNotes) {
                int base = 20;
                base += Math.min(40, (int) totalNotes * 3);
                base += Math.min(25, (int) weekNotes * 5);
                return Math.max(0, Math.min(100, base));
        }

        @Override
        public Map<String, Object> getCrossSubjectHeatmap(Long userId) {
                String[] subjects = { "math", "ds", "co", "os", "cn", "english" };
                String[] labels = { "数学", "数据结构", "组成原理", "操作系统", "计算机网络", "英语" };

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
                                        Set<String> tagSet = subjectTags.get(subj);
                                        for (int i = 0; i < tags.size(); i++) {
                                                tagSet.add(tags.getString(i));
                                        }
                                } catch (Exception ignored) {
                                }
                        }
                }

                List<Map<String, Object>> matrix = new ArrayList<>();
                for (int i = 0; i < subjects.length; i++) {
                        for (int j = 0; j < subjects.length; j++) {
                                Set<String> setI = subjectTags.get(subjects[i]);
                                Set<String> setJ = subjectTags.get(subjects[j]);
                                Set<String> intersection = new HashSet<>(setI);
                                intersection.retainAll(setJ);
                                int strength = intersection.size();

                                Map<String, Object> cell = new HashMap<>();
                                cell.put("row", i);
                                cell.put("col", j);
                                cell.put("rowSubject", subjects[i]);
                                cell.put("colSubject", subjects[j]);
                                cell.put("rowLabel", labels[i]);
                                cell.put("colLabel", labels[j]);
                                cell.put("strength", strength);
                                cell.put("sharedTags", new ArrayList<>(intersection));
                                matrix.add(cell);
                        }
                }

                Map<String, Object> result = new HashMap<>();
                result.put("subjects", subjects);
                result.put("labels", labels);
                result.put("matrix", matrix);
                return result;
        }

        @Override
        public Map<String, Object> getNotesGrouped(Long userId, String subject) {
                LambdaQueryWrapper<AiNote> wrapper = new LambdaQueryWrapper<AiNote>()
                                .eq(AiNote::getUserId, userId)
                                .ne(AiNote::getNoteType, "daily_summary")
                                .orderByDesc(AiNote::getCreateTime);
                if (subject != null && !subject.isEmpty() && !"all".equals(subject)) {
                        if ("other".equals(subject)) {
                                wrapper.notIn(AiNote::getSubject, "math", "ds", "os", "co", "cn", "english", "reading",
                                                "408",
                                                "408exam");
                        } else {
                                wrapper.eq(AiNote::getSubject, subject);
                        }
                }
                List<AiNote> allNotes = aiNoteMapper.selectList(wrapper);

                Map<String, Map<String, List<Map<String, Object>>>> grouped = new LinkedHashMap<>();

                Map<String, String> subjectLabels = new HashMap<>();
                subjectLabels.put("math", "数学");
                subjectLabels.put("ds", "数据结构");
                subjectLabels.put("co", "计算机组成原理");
                subjectLabels.put("os", "操作系统");
                subjectLabels.put("cn", "计算机网络");
                subjectLabels.put("english", "英语");
                subjectLabels.put("408exam", "408综合");
                subjectLabels.put("408", "408综合");
                subjectLabels.put("reading", "精读");
                subjectLabels.put("general", "通用");

                for (AiNote note : allNotes) {
                        String subj = note.getSubject() != null ? note.getSubject() : "general";
                        String subjLabel = subjectLabels.getOrDefault(subj, subj);
                        LocalDateTime ct = note.getCreateTime() != null ? note.getCreateTime()
                                        : (note.getUpdateTime() != null ? note.getUpdateTime()
                                                        : java.time.LocalDateTime.now());
                        String dateKey = ct.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);

                        grouped.computeIfAbsent(subjLabel, k -> new LinkedHashMap<>());
                        grouped.get(subjLabel).computeIfAbsent(dateKey, k -> new ArrayList<>());

                        Map<String, Object> item = new HashMap<>();
                        item.put("id", note.getId());
                        item.put("subject", subj);
                        item.put("questionText", note.getQuestionText());
                        item.put("keyPoints", note.getKeyPoints());
                        item.put("knowledgeTags", note.getKnowledgeTags());
                        item.put("noteType", note.getNoteType());
                        item.put("createTime", ct.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                        item.put("updateTime", note.getUpdateTime() != null
                                        ? note.getUpdateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                        : null);
                        grouped.get(subjLabel).get(dateKey).add(item);
                }

                Map<String, Object> result = new HashMap<>();
                result.put("grouped", grouped);
                result.put("total", allNotes.size());
                return result;
        }

        @Override
        public Map<String, Object> getNoteDetail(Long userId, Long noteId) {
                AiNote note = aiNoteMapper.selectById(noteId);
                if (note == null || !note.getUserId().equals(userId)) {
                        return null;
                }

                Map<String, Object> detail = new HashMap<>();
                detail.put("id", note.getId());
                detail.put("subject", note.getSubject());
                detail.put("questionText", note.getQuestionText());
                detail.put("questionImage", note.getQuestionImage());
                detail.put("knowledgeTags", note.getKnowledgeTags());
                detail.put("keyPoints", note.getKeyPoints());
                detail.put("aiContent", note.getAiContent());
                detail.put("userNotes", note.getUserNotes());
                detail.put("followUpSummary", note.getFollowUpSummary());
                detail.put("noteType", note.getNoteType());
                detail.put("isAutoExtracted", note.getIsAutoExtracted());
                detail.put("sourceArticleId", note.getSourceArticleId());
                detail.put("createTime", note.getCreateTime() != null
                                ? note.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                : null);
                detail.put("updateTime", note.getUpdateTime() != null
                                ? note.getUpdateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                : null);

                if (note.getGroupId() != null) {
                        List<ChatMessage> groupMessages = chatMessageMapper
                                        .selectList(new LambdaQueryWrapper<ChatMessage>()
                                                        .eq(ChatMessage::getGroupId, note.getGroupId())
                                                        .eq(ChatMessage::getUserId, userId)
                                                        .orderByAsc(ChatMessage::getCreateTime));
                        detail.put("messageCount", groupMessages.size());

                        if ((note.getFollowUpSummary() == null || note.getFollowUpSummary().isEmpty())
                                        && groupMessages.size() > 2) {
                                long userMsgCount = groupMessages.stream()
                                                .filter(m -> "USER".equalsIgnoreCase(m.getRole()))
                                                .count();
                                if (userMsgCount > 1) {
                                        try {
                                                StringBuilder conversationLog = new StringBuilder();
                                                for (ChatMessage m : groupMessages) {
                                                        String role = "USER".equalsIgnoreCase(m.getRole()) ? "用户"
                                                                        : "AI";
                                                        String content = m.getContent();
                                                        if (content != null && content.length() > 200) {
                                                                content = content.substring(0, 200) + "...";
                                                        }
                                                        conversationLog.append(role).append("：").append(content)
                                                                        .append("\n");
                                                }
                                                String summaryPrompt = "请将以下多轮对话归纳为简洁的追问摘要，保留关键问题和AI的核心回答要点（不超过300字）：\n\n"
                                                                + conversationLog.toString();
                                                String summary = aiService.chat("你是归纳总结专家，只输出摘要内容。",
                                                                summaryPrompt, "followup-detail-" + userId);
                                                if (summary != null && !summary.isEmpty()) {
                                                        note.setFollowUpSummary(summary.trim());
                                                        aiNoteMapper.updateById(note);
                                                        detail.put("followUpSummary", summary.trim());
                                                }
                                        } catch (Exception e) {
                                                log.warn("同步生成追问摘要失败: {}", e.getMessage());
                                        }
                                }
                        }
                }

                return detail;
        }

        @Override
        public void saveDailySummaryAsNote(Long userId) {
                LocalDateTime todayStart = LocalDate.now().atStartOfDay();

                AiNote existingSummary = aiNoteMapper.selectOne(new LambdaQueryWrapper<AiNote>()
                                .eq(AiNote::getUserId, userId)
                                .eq(AiNote::getNoteType, "daily_summary")
                                .ge(AiNote::getCreateTime, todayStart));

                List<AiNote> todayNotes = aiNoteMapper.selectList(new LambdaQueryWrapper<AiNote>()
                                .eq(AiNote::getUserId, userId)
                                .ge(AiNote::getCreateTime, todayStart)
                                .orderByDesc(AiNote::getCreateTime));

                long todayWords = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                                .eq(WordBook::getUserId, userId)
                                .ge(WordBook::getCreateTime, todayStart));

                long todaySentences = longSentenceMapper.selectCount(new LambdaQueryWrapper<LongSentence>()
                                .eq(LongSentence::getUserId, userId)
                                .ge(LongSentence::getCreateTime, todayStart));

                long todaySearches = searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                                .eq(SearchHistory::getUserId, userId)
                                .ge(SearchHistory::getCreateTime, todayStart));

                long todayReadingNotes = readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>()
                                .eq(ReadingNote::getUserId, userId)
                                .ge(ReadingNote::getCreateTime, todayStart));

                long todayChatMessages = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getUserId, userId)
                                .eq(ChatMessage::getRole, "USER")
                                .ge(ChatMessage::getCreateTime, todayStart));

                long todayReviewCompleted = count(new LambdaQueryWrapper<LearningRecord>()
                                .eq(LearningRecord::getUserId, userId)
                                .ge(LearningRecord::getCreateTime, todayStart));

                Set<String> allTags = new HashSet<>();
                for (AiNote note : todayNotes) {
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

                StringBuilder summaryContent = new StringBuilder();
                summaryContent.append("今日学习总结\n\n");
                summaryContent.append("【学习概览】\n");
                summaryContent.append("- 记录笔记：").append(todayNotes.size()).append("条\n");
                summaryContent.append("- 新增生词：").append(todayWords).append("个\n");
                summaryContent.append("- 收藏长难句：").append(todaySentences).append("句\n");
                summaryContent.append("- 查词次数：").append(todaySearches).append("次\n");
                summaryContent.append("- 阅读笔记：").append(todayReadingNotes).append("条\n");
                summaryContent.append("- AI对话：").append(todayChatMessages).append("条\n");
                summaryContent.append("- 复习完成：").append(todayReviewCompleted).append("项\n\n");

                Map<String, String> subjectLabels = new HashMap<>();
                subjectLabels.put("math", "数学");
                subjectLabels.put("ds", "数据结构");
                subjectLabels.put("co", "计算机组成原理");
                subjectLabels.put("os", "操作系统");
                subjectLabels.put("cn", "计算机网络");
                subjectLabels.put("english", "英语");

                Map<String, List<AiNote>> bySubject = todayNotes.stream()
                                .collect(Collectors
                                                .groupingBy(n -> n.getSubject() != null ? n.getSubject() : "general"));

                if (!bySubject.isEmpty()) {
                        summaryContent.append("【学科分布】\n");
                        for (Map.Entry<String, List<AiNote>> entry : bySubject.entrySet()) {
                                String label = subjectLabels.getOrDefault(entry.getKey(), entry.getKey());
                                summaryContent.append("- ").append(label).append(": ").append(entry.getValue().size())
                                                .append("条笔记\n");
                        }
                        summaryContent.append("\n");
                }

                if (!allTags.isEmpty()) {
                        summaryContent.append("【关键知识点】\n");
                        summaryContent.append(String.join("、", allTags)).append("\n\n");
                }

                int totalActivities = todayNotes.size() + (int) todayWords + (int) todaySentences
                                + (int) todaySearches + (int) todayReadingNotes + (int) todayChatMessages;
                String activityLevel;
                if (totalActivities >= 20) {
                        activityLevel = "🔥 学习热情高涨";
                } else if (totalActivities >= 10) {
                        activityLevel = "👍 学习状态良好";
                } else if (totalActivities >= 5) {
                        activityLevel = "📖 保持学习节奏";
                } else if (totalActivities > 0) {
                        activityLevel = "💪 今天有在学习，继续加油";
                } else {
                        activityLevel = "🌅 新的一天，开始学习吧";
                }
                summaryContent.append("【今日状态】").append(activityLevel).append("\n");

                if (existingSummary != null) {
                        existingSummary.setAiContent(summaryContent.toString());
                        existingSummary.setKnowledgeTags(JSON.toJSONString(new ArrayList<>(allTags)));
                        existingSummary.setUpdateTime(LocalDateTime.now());
                        aiNoteMapper.updateById(existingSummary);
                } else {
                        AiNote summaryNote = new AiNote();
                        summaryNote.setUserId(userId);
                        summaryNote.setSubject("general");
                        summaryNote.setNoteType("daily_summary");
                        summaryNote.setQuestionText(
                                        "每日学习总结 - " + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
                        summaryNote.setAiContent(summaryContent.toString());
                        summaryNote.setKnowledgeTags(JSON.toJSONString(new ArrayList<>(allTags)));
                        summaryNote.setIsAutoExtracted(1);
                        aiNoteMapper.insert(summaryNote);
                }
        }

        @Override
        public Map<String, Object> syncLearningRecords(Long userId) {
                Map<String, Object> result = new HashMap<>();
                int chatCount = 0;
                int searchCount = 0;
                int noteCount = 0;
                int reviewCount = 0;

                List<ChatMessage> chatMessages = chatMessageMapper.selectList(
                                new LambdaQueryWrapper<ChatMessage>()
                                                .eq(ChatMessage::getUserId, userId)
                                                .eq(ChatMessage::getRole, "USER")
                                                .orderByAsc(ChatMessage::getCreateTime));
                if (chatMessages != null) {
                        for (ChatMessage msg : chatMessages) {
                                LocalDateTime msgTime = msg.getCreateTime();
                                long exists = count(new LambdaQueryWrapper<LearningRecord>()
                                                .eq(LearningRecord::getUserId, userId)
                                                .eq(LearningRecord::getRecordType, "chat")
                                                .eq(LearningRecord::getCreateTime, msgTime));
                                if (exists == 0) {
                                        LearningRecord record = new LearningRecord();
                                        record.setUserId(userId);
                                        record.setRecordType("chat");
                                        record.setContentSummary("AI对话");
                                        record.setCreateTime(msgTime);
                                        record.setUpdateTime(msgTime);
                                        save(record);
                                        chatCount++;
                                }
                        }
                }

                List<SearchHistory> searchHistories = searchHistoryMapper.selectList(
                                new LambdaQueryWrapper<SearchHistory>()
                                                .eq(SearchHistory::getUserId, userId)
                                                .orderByAsc(SearchHistory::getCreateTime));
                if (searchHistories != null) {
                        for (SearchHistory sh : searchHistories) {
                                LocalDateTime shTime = sh.getCreateTime();
                                long exists = count(new LambdaQueryWrapper<LearningRecord>()
                                                .eq(LearningRecord::getUserId, userId)
                                                .eq(LearningRecord::getRecordType, "word_search")
                                                .eq(LearningRecord::getCreateTime, shTime));
                                if (exists == 0) {
                                        LearningRecord record = new LearningRecord();
                                        record.setUserId(userId);
                                        record.setRecordType("word_search");
                                        record.setContentSummary(sh.getKeyword());
                                        record.setCreateTime(shTime);
                                        record.setUpdateTime(shTime);
                                        save(record);
                                        searchCount++;
                                }
                        }
                }

                List<AiNote> aiNotes = aiNoteMapper.selectList(
                                new LambdaQueryWrapper<AiNote>()
                                                .eq(AiNote::getUserId, userId)
                                                .orderByAsc(AiNote::getCreateTime));
                if (aiNotes != null) {
                        for (AiNote note : aiNotes) {
                                LocalDateTime noteTime = note.getCreateTime();
                                long exists = count(new LambdaQueryWrapper<LearningRecord>()
                                                .eq(LearningRecord::getUserId, userId)
                                                .eq(LearningRecord::getRecordType, "note")
                                                .eq(LearningRecord::getCreateTime, noteTime));
                                if (exists == 0) {
                                        LearningRecord record = new LearningRecord();
                                        record.setUserId(userId);
                                        record.setRecordType("note");
                                        record.setContentSummary(note.getTitle());
                                        record.setCreateTime(noteTime);
                                        record.setUpdateTime(noteTime);
                                        save(record);
                                        noteCount++;
                                }
                        }
                }

                List<ReviewTask> reviewTasks = reviewTaskMapper.selectList(
                                new LambdaQueryWrapper<ReviewTask>()
                                                .eq(ReviewTask::getUserId, userId)
                                                .orderByAsc(ReviewTask::getCreateTime));
                if (reviewTasks != null) {
                        for (ReviewTask task : reviewTasks) {
                                LocalDateTime taskTime = task.getCreateTime();
                                long exists = count(new LambdaQueryWrapper<LearningRecord>()
                                                .eq(LearningRecord::getUserId, userId)
                                                .eq(LearningRecord::getRecordType, "review")
                                                .eq(LearningRecord::getCreateTime, taskTime));
                                if (exists == 0) {
                                        LearningRecord record = new LearningRecord();
                                        record.setUserId(userId);
                                        record.setRecordType("review");
                                        record.setContentSummary(task.getContent());
                                        record.setCreateTime(taskTime);
                                        record.setUpdateTime(taskTime);
                                        save(record);
                                        reviewCount++;
                                }
                        }
                }

                result.put("chatSynced", chatCount);
                result.put("searchSynced", searchCount);
                result.put("noteSynced", noteCount);
                result.put("reviewSynced", reviewCount);
                result.put("totalSynced", chatCount + searchCount + noteCount + reviewCount);
                log.info("数据同步完成: userId={}, chat={}, search={}, note={}, review={}",
                                userId, chatCount, searchCount, noteCount, reviewCount);
                return result;
        }
}
