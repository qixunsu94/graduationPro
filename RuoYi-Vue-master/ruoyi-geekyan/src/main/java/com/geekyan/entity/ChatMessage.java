package com.geekyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String sessionId;
    private String groupId;
    private Long userId;
    private String content;
    private String role;
    private String fileName;
    private String imageUrl;
    private String grammarAnalysis;
    private String translation;
    private String sections;
    private String hiddenJson;
    private Integer pronunciationScore;
    private String sendMessageId;
    private String subject;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;
}
