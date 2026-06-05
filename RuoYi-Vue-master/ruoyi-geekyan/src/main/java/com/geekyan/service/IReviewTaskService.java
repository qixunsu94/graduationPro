package com.geekyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.geekyan.entity.ReviewTask;
import java.util.List;
import java.util.Map;

public interface IReviewTaskService extends IService<ReviewTask> {
    List<ReviewTask> getTodayReviews(Long userId, String subject);

    void completeReview(Long taskId, Integer score);

    void createReviewTask(Long userId, String relatedType, Long relatedId, String content, String subject);

    void createReviewTask(Long userId, String relatedType, Long relatedId, String content, String answerContent, String subject);

    Map<String, Object> getReviewStats(Long userId);

    Map<String, Object> getReviewDetail(Long userId, Long taskId);

    String aiExplainReview(Long userId, Long taskId);
}
