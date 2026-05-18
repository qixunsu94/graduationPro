package com.geekyan.controller;

import com.geekyan.entity.ReviewTask;
import com.geekyan.service.IReviewTaskService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/geekyan/review")
public class ReviewController extends BaseController {

    @Autowired
    private IReviewTaskService reviewTaskService;

    @GetMapping("/today")
    public TableDataInfo todayReviews() {
        startPage();
        List<ReviewTask> list = reviewTaskService.getTodayReviews(getUserId());
        return getDataTable(list);
    }

    @PostMapping("/complete/{taskId}")
    public AjaxResult completeReview(@PathVariable Long taskId, @RequestParam Integer score) {
        reviewTaskService.completeReview(taskId, score);
        return success();
    }

    @GetMapping("/stats")
    public AjaxResult stats() {
        Map<String, Object> stats = reviewTaskService.getReviewStats(getUserId());
        return success(stats);
    }
}
