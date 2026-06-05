package com.geekyan.controller;

import com.geekyan.entity.ReviewTask;
import com.geekyan.service.IReviewTaskService;
import com.geekyan.service.ILearningRecordService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/geekyan/review")
public class ReviewController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    @Autowired
    private IReviewTaskService reviewTaskService;

    @Autowired
    private ILearningRecordService learningRecordService;

    @GetMapping("/today")
    public TableDataInfo todayReviews(@RequestParam(required = false) String subject) {
        startPage();
        List<ReviewTask> list = reviewTaskService.getTodayReviews(getUserId(), subject);
        return getDataTable(list);
    }

    @PostMapping("/complete/{taskId}")
    public AjaxResult completeReview(@PathVariable Long taskId, @RequestParam Integer score) {
        Long userId = getUserId();
        ReviewTask task = reviewTaskService.getById(taskId);
        reviewTaskService.completeReview(taskId, score);
        try {
            learningRecordService.recordLearning(userId, "review", null,
                    task != null ? task.getContent() : "复习完成", score,
                    task != null ? task.getSubject() : null, "review", String.valueOf(taskId));
        } catch (Exception e) {
            log.warn("记录复习行为失败: {}", e.getMessage());
        }
        return success();
    }

    @GetMapping("/stats")
    public AjaxResult stats() {
        Map<String, Object> stats = reviewTaskService.getReviewStats(getUserId());
        return success(stats);
    }

    @GetMapping("/detail/{taskId}")
    public AjaxResult reviewDetail(@PathVariable Long taskId) {
        Map<String, Object> detail = reviewTaskService.getReviewDetail(getUserId(), taskId);
        return success(detail);
    }

    @PostMapping("/ai-explain/{taskId}")
    public AjaxResult aiExplain(@PathVariable Long taskId) {
        String explanation = reviewTaskService.aiExplainReview(getUserId(), taskId);
        return success(explanation);
    }
}
