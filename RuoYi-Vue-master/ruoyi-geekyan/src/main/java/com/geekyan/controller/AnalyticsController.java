package com.geekyan.controller;

import com.geekyan.entity.LearningRecord;
import com.geekyan.entity.ReadingSession;
import com.geekyan.mapper.ReadingSessionMapper;
import com.geekyan.service.AnalyticsAIService;
import com.geekyan.service.ILearningRecordService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/geekyan/analytics")
public class AnalyticsController extends BaseController {

    @Autowired
    private ILearningRecordService learningRecordService;

    @Autowired
    private AnalyticsAIService analyticsAIService;

    @Autowired
    private ReadingSessionMapper readingSessionMapper;

    @GetMapping("/dashboard")
    public AjaxResult dashboard() {
        Map<String, Object> dashboard = learningRecordService.getDashboard(getUserId());
        return success(dashboard);
    }

    @GetMapping("/radar")
    public AjaxResult radar(@RequestParam(defaultValue = "week") String timeRange) {
        Map<String, Object> radar = learningRecordService.getRadarData(getUserId(), timeRange);
        return success(radar);
    }

    @GetMapping("/heatmap")
    public AjaxResult heatmap() {
        Map<String, Object> heatmap = learningRecordService.getHeatmapData(getUserId());
        return success(heatmap);
    }

    @GetMapping("/knowledge-tags")
    public AjaxResult knowledgeTags() {
        return success(learningRecordService.getKnowledgeTags(getUserId()));
    }

    @GetMapping("/ai-analysis")
    public AjaxResult aiAnalysis() {
        Map<String, Object> analysis = learningRecordService.getAIAnalysis(getUserId());
        return success(analysis);
    }

    @GetMapping("/english-stats")
    public AjaxResult englishStats() {
        Map<String, Object> stats = learningRecordService.getEnglishStats(getUserId());
        return success(stats);
    }

    @GetMapping("/reading-analysis")
    public AjaxResult readingAnalysis() {
        Map<String, Object> analysis = learningRecordService.getReadingAnalysis(getUserId());
        return success(analysis);
    }

    @PostMapping("/record")
    public AjaxResult recordLearning(@RequestBody LearningRecord record) {
        learningRecordService.recordLearning(getUserId(), record.getRecordType(),
                record.getDuration(), record.getContentSummary(), record.getScore(),
                record.getSubject(), record.getSourceType(), record.getSourceId());
        return success();
    }

    @GetMapping("/subject-analysis")
    public AjaxResult subjectAnalysis(@RequestParam String subject) {
        Map<String, Object> analysis = learningRecordService.getSubjectAnalysis(getUserId(), subject);
        return success(analysis);
    }

    @GetMapping("/cross-subject-heatmap")
    public AjaxResult crossSubjectHeatmap() {
        Map<String, Object> heatmap = learningRecordService.getCrossSubjectHeatmap(getUserId());
        return success(heatmap);
    }

    @GetMapping("/notes-grouped")
    public AjaxResult notesGrouped(@RequestParam(required = false) String subject) {
        Map<String, Object> grouped = learningRecordService.getNotesGrouped(getUserId(), subject);
        return success(grouped);
    }

    @GetMapping("/note-detail/{id}")
    public AjaxResult noteDetail(@PathVariable Long id) {
        Map<String, Object> detail = learningRecordService.getNoteDetail(getUserId(), id);
        if (detail == null) {
            return error("笔记不存在");
        }
        return success(detail);
    }

    @PostMapping("/save-daily-summary")
    public AjaxResult saveDailySummary() {
        learningRecordService.saveDailySummaryAsNote(getUserId());
        return success();
    }

    @PostMapping("/refresh-ai-cache")
    public AjaxResult refreshAICache() {
        analyticsAIService.evictAllCache(getUserId());
        return success("AI分析缓存已清除，下次请求将重新生成");
    }

    @PostMapping("/sync-records")
    public AjaxResult syncRecords() {
        Map<String, Object> result = learningRecordService.syncLearningRecords(getUserId());
        return success(result);
    }

    // ==================== 阅读会话接口 ====================

    /** 开始阅读会话 */
    @PostMapping("/reading-session/start")
    public AjaxResult startReadingSession(@RequestBody Map<String, Object> params) {
        Long userId = getUserId();
        ReadingSession session = new ReadingSession();
        session.setUserId(userId);
        session.setDocumentId(params.get("documentId") != null ? Long.valueOf(params.get("documentId").toString()) : null);
        session.setSourceFile(params.get("sourceFile") != null ? params.get("sourceFile").toString() : null);
        session.setStartTime(LocalDateTime.now());
        session.setDurationSeconds(0);
        session.setActiveSeconds(0);
        readingSessionMapper.insert(session);
        AjaxResult result = success();
        result.put("sessionId", session.getId());
        return result;
    }

    /** 心跳：每30秒调用一次，累加阅读时长 */
    @PostMapping("/reading-session/heartbeat")
    public AjaxResult heartbeatReadingSession(@RequestBody Map<String, Object> params) {
        Long sessionId = Long.valueOf(params.get("sessionId").toString());
        Integer increment = params.get("increment") != null ? Integer.valueOf(params.get("increment").toString()) : 30;
        ReadingSession session = readingSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(getUserId())) {
            return error("会话不存在");
        }
        session.setDurationSeconds(session.getDurationSeconds() + increment);
        session.setActiveSeconds(session.getActiveSeconds() + increment);
        readingSessionMapper.updateById(session);
        return success();
    }

    /** 结束阅读会话 */
    @PostMapping("/reading-session/end")
    public AjaxResult endReadingSession(@RequestBody Map<String, Object> params) {
        Long sessionId = Long.valueOf(params.get("sessionId").toString());
        Integer finalDuration = params.get("duration") != null ? Integer.valueOf(params.get("duration").toString()) : null;
        ReadingSession session = readingSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(getUserId())) {
            return error("会话不存在");
        }
        session.setEndTime(LocalDateTime.now());
        if (finalDuration != null) {
            session.setDurationSeconds(finalDuration);
            session.setActiveSeconds(finalDuration);
        }
        readingSessionMapper.updateById(session);
        return success();
    }
}
