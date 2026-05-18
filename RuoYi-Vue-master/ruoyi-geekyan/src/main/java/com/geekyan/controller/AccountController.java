package com.geekyan.controller;

import com.geekyan.entity.UserSettings;
import com.geekyan.entity.UserCollect;
import com.geekyan.entity.Feedback;
import com.geekyan.service.IUserSettingsService;
import com.geekyan.service.IUserCollectService;
import com.geekyan.service.IFeedbackService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/geekyan/account")
public class AccountController extends BaseController {

    @Autowired
    private IUserSettingsService userSettingsService;

    @Autowired
    private IUserCollectService userCollectService;

    @Autowired
    private IFeedbackService feedbackService;

    @GetMapping("/settings")
    public AjaxResult getSettings() {
        UserSettings settings = userSettingsService.getOrCreateByUserId(getUserId());
        return success(settings);
    }

    @PutMapping("/settings")
    public AjaxResult updateSettings(@RequestBody UserSettings settings) {
        settings.setUserId(getUserId());
        return toAjax(userSettingsService.updateById(settings));
    }

    @GetMapping("/collects")
    public TableDataInfo collectList() {
        startPage();
        List<UserCollect> list = userCollectService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserCollect>()
                        .eq(UserCollect::getUserId, getUserId())
                        .orderByDesc(UserCollect::getCreateTime));
        return getDataTable(list);
    }

    @PostMapping("/collects")
    public AjaxResult addCollect(@RequestBody UserCollect collect) {
        collect.setUserId(getUserId());
        return toAjax(userCollectService.save(collect));
    }

    @DeleteMapping("/collects/{id}")
    public AjaxResult removeCollect(@PathVariable Long id) {
        return toAjax(userCollectService.removeById(id));
    }

    @PostMapping("/feedback")
    public AjaxResult submitFeedback(@RequestBody Feedback feedback) {
        feedback.setUserId(getUserId());
        feedback.setStatus(0);
        return toAjax(feedbackService.save(feedback));
    }
}
