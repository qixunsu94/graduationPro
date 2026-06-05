package com.geekyan.service;

import com.geekyan.vo.PracticeEndResponse;
import com.geekyan.vo.PracticeStartResponse;
import java.util.Map;

/**
 * AI对练Service接口
 */
public interface IPracticeService {

    /**
     * 开始一个新的AI对练会话（注入用户个人数据）
     */
    PracticeStartResponse startPractice(String topic, String subject);

    /**
     * 提交本轮对练回答并获取下一步追问
     */
    Map<String, Object> answerPractice(String sessionId, String message, String imageBase64);

    /**
     * 结束AI对练会话，生成课后评估报告
     */
    PracticeEndResponse endPractice(String sessionId);
}
