package com.geekyan.controller;

import com.geekyan.dto.PracticeStartRequest;
import com.geekyan.service.IPracticeService;
import com.geekyan.vo.PracticeEndResponse;
import com.geekyan.vo.PracticeStartResponse;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI对练Controller - 支持注入用户个人数据的个性化对练
 */
@RestController
@RequestMapping("/geekyan/practice")
public class PracticeController extends BaseController {

    @Autowired
    private IPracticeService practiceService;

    /**
     * 开始一个新的AI对练会话
     * 会自动聚合用户相关学习数据（聊天记录、复习任务、笔记）注入AI Prompt
     */
    @PostMapping("/start")
    public AjaxResult startPractice(@RequestBody PracticeStartRequest request) {
        try {
            PracticeStartResponse response = practiceService.startPractice(
                    request.getTopic(), request.getSubject());
            return AjaxResult.success(response);
        } catch (Exception e) {
            logger.error("开始AI对练失败", e);
            return AjaxResult.error("初始化对练失败: " + e.getMessage());
        }
    }

    /**
     * 提交AI对练回答，继续一问一答链路
     */
    @PostMapping("/answer")
    public AjaxResult answerPractice(@RequestBody Map<String, Object> params) {
        try {
            String sessionId = (String) params.get("sessionId");
            String message = (String) params.get("message");
            String imageBase64 = (String) params.get("image_base64");
            if (sessionId == null || sessionId.isEmpty()) {
                return AjaxResult.error("会话ID不能为空");
            }
            if ((message == null || message.trim().isEmpty()) && (imageBase64 == null || imageBase64.isEmpty())) {
                return AjaxResult.error("回答内容不能为空");
            }
            return AjaxResult.success(practiceService.answerPractice(sessionId, message, imageBase64));
        } catch (Exception e) {
            logger.error("AI对练回答失败", e);
            return AjaxResult.error("提交对练回答失败: " + e.getMessage());
        }
    }

    /**
     * 结束AI对练会话，生成课后评估报告
     * 会根据对练表现更新相关复习任务的掌握度
     */
    @PostMapping("/end")
    public AjaxResult endPractice(@RequestBody Map<String, String> params) {
        try {
            String sessionId = params.get("sessionId");
            if (sessionId == null || sessionId.isEmpty()) {
                return AjaxResult.error("会话ID不能为空");
            }
            PracticeEndResponse response = practiceService.endPractice(sessionId);
            return AjaxResult.success(response);
        } catch (Exception e) {
            logger.error("结束AI对练失败", e);
            return AjaxResult.error("生成评估报告失败: " + e.getMessage());
        }
    }
}
