package com.geekyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.LearningRecord;
import com.geekyan.mapper.LearningRecordMapper;
import com.geekyan.service.ILearningRecordService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    @Override
    public void recordLearning(Long userId, String type, Integer duration, String summary, Integer score) {
        LearningRecord record = new LearningRecord();
        record.setUserId(userId);
        record.setRecordType(type);
        record.setDuration(duration);
        record.setContentSummary(summary);
        record.setScore(score);
        save(record);
    }

    @Override
    public Map<String, Object> getDashboard(Long userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayPractice = count(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId).ge(LearningRecord::getCreateTime, todayStart));
        long totalPractice = count(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId));
        Map<String, Object> overview = new HashMap<>();
        overview.put("totalPractice", totalPractice);
        overview.put("todayPractice", todayPractice);
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("overview", overview);
        return dashboard;
    }

    @Override
    public Map<String, Object> getRadarData(Long userId) {
        Map<String, Object> radar = new HashMap<>();
        radar.put("english", 75);
        radar.put("math", 60);
        radar.put("cs", 70);
        radar.put("vocabulary", 80);
        radar.put("grammar", 65);
        radar.put("reading", 72);
        return radar;
    }
}
