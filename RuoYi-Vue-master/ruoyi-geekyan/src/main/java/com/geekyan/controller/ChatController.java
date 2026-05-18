package com.geekyan.controller;

import com.geekyan.entity.ChatSession;
import com.geekyan.entity.ChatMessage;
import com.geekyan.service.IChatSessionService;
import com.geekyan.service.IChatMessageService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/geekyan/chat")
public class ChatController extends BaseController {

    @Autowired
    private IChatSessionService chatSessionService;

    @Autowired
    private IChatMessageService chatMessageService;

    @GetMapping("/sessions")
    public TableDataInfo sessionList() {
        startPage();
        Long userId = getUserId();
        List<ChatSession> list = chatSessionService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getCreateTime));
        return getDataTable(list);
    }

    @PostMapping("/sessions")
    public AjaxResult createSession(@RequestBody ChatSession session) {
        session.setUserId(getUserId());
        session.setSessionId(java.util.UUID.randomUUID().toString().replace("-", ""));
        return toAjax(chatSessionService.save(session));
    }

    @GetMapping("/sessions/{sessionId}")
    public AjaxResult getSessionDetail(@PathVariable String sessionId) {
        Map<String, Object> detail = chatSessionService.getSessionDetail(sessionId, getUserId());
        return success(detail);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public AjaxResult deleteSession(@PathVariable String sessionId) {
        return toAjax(chatSessionService.remove(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getUserId, getUserId())));
    }

    @GetMapping("/messages/{sessionId}")
    public TableDataInfo messageList(@PathVariable String sessionId) {
        startPage();
        List<ChatMessage> list = chatMessageService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getUserId, getUserId())
                        .orderByAsc(ChatMessage::getCreateTime));
        return getDataTable(list);
    }

    @PostMapping("/send")
    public AjaxResult sendMessage(@RequestBody Map<String, String> params) {
        String sessionId = params.get("session_id");
        String message = params.get("message");
        String fileName = params.get("file_name");
        Map<String, Object> result = chatMessageService.sendMessage(getUserId(), sessionId, message, fileName);
        return success(result);
    }

    @PostMapping("/translate/{messageId}")
    public AjaxResult translateMessage(@PathVariable String messageId) {
        return success(chatMessageService.translateMessage(messageId));
    }

    @PostMapping("/practice/{messageId}")
    public AjaxResult practiceMessage(@PathVariable String messageId) {
        return success(chatMessageService.practiceMessage(messageId));
    }

    @DeleteMapping("/messages/latest/{sessionId}")
    public AjaxResult deleteLatestMessage(@PathVariable String sessionId) {
        chatMessageService.deleteLatestMessage(sessionId);
        return success();
    }

    @DeleteMapping("/messages/all/{sessionId}")
    public AjaxResult deleteAllMessages(@PathVariable String sessionId) {
        chatMessageService.deleteAllMessages(sessionId);
        return success();
    }
}
