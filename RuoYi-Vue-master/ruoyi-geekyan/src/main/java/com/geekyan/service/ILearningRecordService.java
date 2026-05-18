package com.geekyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.geekyan.entity.LearningRecord;
import java.util.Map;

public interface ILearningRecordService extends IService<LearningRecord> {
    void recordLearning(Long userId, String type, Integer duration, String summary, Integer score);
    Map<String, Object> getDashboard(Long userId);
    Map<String, Object> getRadarData(Long userId);
}
