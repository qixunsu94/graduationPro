package com.geekyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.ReviewTask;
import com.geekyan.mapper.ReviewTaskMapper;
import com.geekyan.service.IReviewTaskService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReviewTaskServiceImpl extends ServiceImpl<ReviewTaskMapper, ReviewTask> implements IReviewTaskService {

    private static final int[] EBBINGHAUS_INTERVALS = {1, 2, 4, 7, 15, 30};

    @Override
    public List<ReviewTask> getTodayReviews(Long userId) {
        return list(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId)
                .eq(ReviewTask::getIsCompleted, 0)
                .le(ReviewTask::getNextReviewTime, LocalDateTime.now())
                .orderByAsc(ReviewTask::getNextReviewTime));
    }

    @Override
    public void completeReview(Long taskId, Integer score) {
        ReviewTask task = getById(taskId);
        if (task == null) return;
        task.setLastReviewTime(LocalDateTime.now());
        task.setReviewStage(task.getReviewStage() + 1);
        task.setAccuracyScore(score);
        if (task.getReviewStage() > EBBINGHAUS_INTERVALS.length) {
            task.setIsCompleted(1);
        } else {
            int days = EBBINGHAUS_INTERVALS[task.getReviewStage() - 1];
            task.setNextReviewTime(LocalDateTime.now().plusDays(days));
        }
        updateById(task);
    }

    @Override
    public Map<String, Object> getReviewStats(Long userId) {
        long masteredCount = count(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId).eq(ReviewTask::getIsCompleted, 1));
        long activeCount = count(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId).eq(ReviewTask::getIsCompleted, 0));
        Map<String, Object> stats = new HashMap<>();
        stats.put("masteredCount", masteredCount);
        stats.put("activeCount", activeCount);
        return stats;
    }
}
