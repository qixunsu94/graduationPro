package com.geekyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.geekyan.entity.ReviewTask;
import java.util.List;
import java.util.Map;

public interface IReviewTaskService extends IService<ReviewTask> {
    List<ReviewTask> getTodayReviews(Long userId);
    void completeReview(Long taskId, Integer score);
    Map<String, Object> getReviewStats(Long userId);
}
