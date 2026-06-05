package com.geekyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.geekyan.entity.LearningRecord;
import java.util.List;
import java.util.Map;

public interface ILearningRecordService extends IService<LearningRecord> {
    void recordLearning(Long userId, String type, Integer duration, String summary, Integer score);

    void recordLearning(Long userId, String type, Integer duration, String summary, Integer score,
            String subject, String sourceType, String sourceId);

    Map<String, Object> getDashboard(Long userId);

    Map<String, Object> getRadarData(Long userId, String timeRange);

    Map<String, Object> getHeatmapData(Long userId);

    List<Map<String, Object>> getKnowledgeTags(Long userId);

    Map<String, Object> getEnglishStats(Long userId);

    Map<String, Object> getReadingAnalysis(Long userId);

    Map<String, Object> getAIAnalysis(Long userId);

    Map<String, Object> getSubjectAnalysis(Long userId, String subject);

    Map<String, Object> getCrossSubjectHeatmap(Long userId);

    Map<String, Object> getNotesGrouped(Long userId, String subject);

    Map<String, Object> getNoteDetail(Long userId, Long noteId);

    void saveDailySummaryAsNote(Long userId);

    Map<String, Object> syncLearningRecords(Long userId);
}
