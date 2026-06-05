package com.geekyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.ReviewTask;
import com.geekyan.entity.WordBook;
import com.geekyan.entity.LongSentence;
import com.geekyan.entity.AiNote;
import com.geekyan.entity.ReadingNote;
import com.geekyan.mapper.ReviewTaskMapper;
import com.geekyan.service.IReviewTaskService;
import com.geekyan.service.IWordBookService;
import com.geekyan.service.ILongSentenceService;
import com.geekyan.service.IAiNoteService;
import com.geekyan.service.IAiService;
import com.geekyan.service.IReadingNoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReviewTaskServiceImpl extends ServiceImpl<ReviewTaskMapper, ReviewTask> implements IReviewTaskService {

    private static final int[] EBBINGHAUS_INTERVALS = { 1, 2, 4, 7, 15, 30 };

    @Autowired
    private IWordBookService wordBookService;

    @Autowired
    private ILongSentenceService longSentenceService;

    @Autowired
    private IAiNoteService aiNoteService;

    @Autowired
    private IAiService aiService;

    @Autowired
    private IReadingNoteService readingNoteService;

    @Override
    public List<ReviewTask> getTodayReviews(Long userId, String subject) {
        LambdaQueryWrapper<ReviewTask> wrapper = new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getIsCompleted, 0)
                .le(ReviewTask::getNextReviewTime, LocalDateTime.now());
        if (subject != null && !subject.isEmpty()) {
            wrapper.eq(ReviewTask::getSubject, subject);
        }
        wrapper.orderByAsc(ReviewTask::getNextReviewTime);
        return list(wrapper);
    }

    @Override
    public void completeReview(Long taskId, Integer score) {
        ReviewTask task = getById(taskId);
        if (task == null)
            return;

        task.setLastReviewTime(LocalDateTime.now());
        task.setReviewCount(task.getReviewCount() != null ? task.getReviewCount() + 1 : 1);
        task.setAccuracyScore(score);

        // 草稿中的正确/错误双路径逻辑
        boolean isCorrect = score != null && score >= 60;
        int currentStage = task.getReviewStage() != null ? task.getReviewStage() : 1;
        double currentMastery = task.getMasteryLevel() != null ? task.getMasteryLevel() : 0.0;

        if (isCorrect) {
            // 回答正确，进入下一阶段
            currentStage++;
            // 掌握度增加，增量与当前阶段有关，越往后增量越小
            currentMastery += (1.0 / (currentStage + 1));
        } else {
            // 回答错误，复习阶段倒退，但不小于1
            currentStage = Math.max(1, currentStage - 1);
            // 掌握度降低
            currentMastery *= 0.8;
        }
        task.setMasteryLevel(Math.min(1.0, Math.round(currentMastery * 100.0) / 100.0));

        if (currentStage > EBBINGHAUS_INTERVALS.length) {
            // 所有复习阶段已完成
            task.setIsCompleted(1);
            task.setNextReviewTime(null);
        } else {
            task.setReviewStage(currentStage);
            int days = EBBINGHAUS_INTERVALS[currentStage - 1];
            task.setNextReviewTime(LocalDateTime.now().plusDays(days));
        }
        task.setUpdateTime(LocalDateTime.now());
        updateById(task);
    }

    @Override
    public void createReviewTask(Long userId, String relatedType, Long relatedId,
            String content, String subject) {
        createReviewTask(userId, relatedType, relatedId, content, null, subject);
    }

    @Override
    public void createReviewTask(Long userId, String relatedType, Long relatedId,
            String content, String answerContent, String subject) {
        ReviewTask existing = getOne(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getRelatedType, relatedType)
                .eq(ReviewTask::getRelatedId, relatedId));
        if (existing != null) {
            return;
        }

        ReviewTask task = new ReviewTask();
        task.setUserId(userId);
        task.setRelatedType(relatedType);
        task.setRelatedId(relatedId);
        task.setContent(content);
        task.setAnswerContent(answerContent);
        task.setSubject(subject);
        task.setReviewStage(1);
        task.setReviewCount(0);
        task.setNextReviewTime(LocalDateTime.now());
        task.setIsCompleted(0);
        task.setMasteryLevel(0.0);
        task.setDelFlag(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        save(task);
    }

    @Override
    public Map<String, Object> getReviewDetail(Long userId, Long taskId) {
        ReviewTask task = getOne(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getId, taskId)
                .eq(ReviewTask::getUserId, userId));
        if (task == null) {
            return null;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("task", task);

        try {
            if ("word".equals(task.getRelatedType())) {
                WordBook wb = wordBookService.getById(task.getRelatedId());
                if (wb != null) {
                    detail.put("wordInfo", wb);
                }
            } else if ("sentence".equals(task.getRelatedType())) {
                LongSentence ls = longSentenceService.getById(task.getRelatedId());
                if (ls != null) {
                    detail.put("sentenceInfo", ls);
                }
            } else if ("note".equals(task.getRelatedType())) {
                AiNote note = aiNoteService.getById(task.getRelatedId());
                if (note != null) {
                    detail.put("noteInfo", note);
                }
            } else if ("reading_note".equals(task.getRelatedType())
                    || "reading_collect".equals(task.getRelatedType())) {
                ReadingNote rn = readingNoteService.getById(task.getRelatedId());
                if (rn != null) {
                    detail.put("readingNoteInfo", rn);
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return detail;
    }

    @Override
    public Map<String, Object> getReviewStats(Long userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        long totalReviews = count(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId));

        long masteredCount = count(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getIsCompleted, 1));

        long activeCount = count(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getIsCompleted, 0));

        long todayCompleted = count(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .ge(ReviewTask::getLastReviewTime, todayStart));

        long todayTotal = count(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getIsCompleted, 0)
                .le(ReviewTask::getNextReviewTime, LocalDateTime.now()));

        long wordsInReview = count(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getRelatedType, "word")
                .eq(ReviewTask::getIsCompleted, 0));

        long notesInReview = count(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getRelatedType, "note")
                .eq(ReviewTask::getIsCompleted, 0));

        long sentencesInReview = count(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getRelatedType, "sentence")
                .eq(ReviewTask::getIsCompleted, 0));

        long readingNotesInReview = count(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getRelatedType, "reading_note")
                .eq(ReviewTask::getIsCompleted, 0));

        long readingCollectInReview = count(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getRelatedType, "reading_collect")
                .eq(ReviewTask::getIsCompleted, 0));

        int streakDays = calculateReviewStreak(userId);

        double avgAccuracy = 0;
        List<ReviewTask> reviewedTasks = list(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .isNotNull(ReviewTask::getAccuracyScore));
        if (!reviewedTasks.isEmpty()) {
            avgAccuracy = reviewedTasks.stream()
                    .mapToInt(t -> t.getAccuracyScore() != null ? t.getAccuracyScore() : 0)
                    .average()
                    .orElse(0);
        }

        double averageMastery = 0;
        List<ReviewTask> allTasks = list(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId));
        if (!allTasks.isEmpty()) {
            averageMastery = allTasks.stream()
                    .mapToDouble(t -> t.getMasteryLevel() != null ? t.getMasteryLevel() : 0.0)
                    .average()
                    .orElse(0) * 5;
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalReviews", totalReviews);
        stats.put("masteredCount", masteredCount);
        stats.put("activeCount", activeCount);
        stats.put("avgAccuracy", Math.round(avgAccuracy));
        stats.put("overallAccuracy", Math.round(avgAccuracy));
        stats.put("averageMastery", Math.round(averageMastery * 10.0) / 10.0);
        stats.put("streakDays", streakDays);
        stats.put("todayCompleted", todayCompleted);
        stats.put("todayTotal", todayTotal);
        stats.put("wordsInReview", wordsInReview);
        stats.put("notesInReview", notesInReview);
        stats.put("sentencesInReview", sentencesInReview);
        stats.put("readingNotesInReview", readingNotesInReview);
        stats.put("readingCollectInReview", readingCollectInReview);
        return stats;
    }

    private int calculateReviewStreak(Long userId) {
        int streak = 0;
        LocalDate date = LocalDate.now();
        while (true) {
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);
            long count = count(new LambdaQueryWrapper<ReviewTask>()
                    .eq(ReviewTask::getUserId, userId)
                    .ge(ReviewTask::getLastReviewTime, dayStart)
                    .lt(ReviewTask::getLastReviewTime, dayEnd));
            if (count > 0) {
                streak++;
                date = date.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }

    @Override
    public String aiExplainReview(Long userId, Long taskId) {
        ReviewTask task = getOne(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getId, taskId)
                .eq(ReviewTask::getUserId, userId));
        if (task == null) {
            return "未找到复习任务";
        }

        String systemPrompt;
        String userPrompt;

        switch (task.getRelatedType()) {
            case "word":
                systemPrompt = "你是一个单词速记助手。用户正在复习一个单词，但掌握得不够好。请用最精炼的方式帮助他记忆。\n" +
                        "输出要求（只输出以下内容，不要超过5行）：\n" +
                        "1. 单词 + 音标\n2. 一个核心释义（一句话）\n" +
                        "3. 一个记忆技巧（谐音/词根/联想，选一种最有效的）\n4. 一个简短例句";
                userPrompt = "我复习的单词是：" + task.getContent() + "\n请帮我快速回忆这个单词。";
                break;

            case "note":
            case "reading_note":
            case "reading_collect":
                systemPrompt = "你是一个考研辅导助手。用户正在复习一道之前做过的题目，但记忆模糊。请用最精炼的方式帮他回顾核心要点。\n" +
                        "输出要求（只输出以下内容）：\n" +
                        "1. 这道题考什么（一句话点明核心知识点）\n" +
                        "2. 解题最关键的一步是什么\n3. 最容易踩的坑是什么";
                String answerHint = task.getAnswerContent() != null ? task.getAnswerContent() : "";
                userPrompt = "我复习的题目是：" + task.getContent() + "\n之前的解题要点是：" + answerHint + "\n请帮我快速回顾这道题。";
                break;

            case "sentence":
                systemPrompt = "你是一个英语语法助手。用户正在复习一个长难句，但记忆模糊。请用最精炼的方式帮他回顾语法要点。\n" +
                        "输出要求（只输出以下内容）：\n" +
                        "1. 句子的核心意思（一句话中文翻译）\n" +
                        "2. 句子中最关键的1-2个语法点\n3. 一个同结构的简单例句";
                userPrompt = "我复习的句子是：" + task.getContent() + "\n请帮我快速回顾这个句子。";
                break;

            default:
                systemPrompt = "你是一个学习助手。用户正在复习一个知识点，请用最精炼的方式帮他回顾。";
                userPrompt = "我复习的内容是：" + task.getContent();
        }

        try {
            return aiService.chatWithoutHistory(systemPrompt, userPrompt);
        } catch (Exception e) {
            return "AI讲解暂时不可用，请稍后重试";
        }
    }
}
