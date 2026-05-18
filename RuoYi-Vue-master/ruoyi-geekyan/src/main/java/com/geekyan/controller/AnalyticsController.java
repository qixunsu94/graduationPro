package com.geekyan.controller;

import com.geekyan.entity.LearningRecord;
import com.geekyan.service.ILearningRecordService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/geekyan/analytics")
public class AnalyticsController extends BaseController {

    @Autowired
    private ILearningRecordService learningRecordService;

    @GetMapping("/dashboard")
    public AjaxResult dashboard() {
        Map<String, Object> dashboard = learningRecordService.getDashboard(getUserId());
        return success(dashboard);
    }

    @GetMapping("/radar")
    public AjaxResult radar() {
        Map<String, Object> radar = learningRecordService.getRadarData(getUserId());
        return success(radar);
    }

    @PostMapping("/record")
    public AjaxResult recordLearning(@RequestBody LearningRecord record) {
        learningRecordService.recordLearning(getUserId(), record.getRecordType(),
                record.getDuration(), record.getContentSummary(), record.getScore());
        return success();
    }
}
