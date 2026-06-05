package com.geekyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_note")
public class AiNote {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String sessionId;
    private String groupId;
    private String subject;
    private String title;
    private String questionText;
    private String questionImage;
    private String knowledgeTags;
    private String keyPoints;
    private String trapTypes;
    private String relatedKnowledge;
    private String coreVocab;
    private String grammarPoints;
    private String difficulty;
    private String aiContent;
    private String userNotes;
    private String followUpSummary;
    private String noteType;
    private String sourceType;
    private Long sourceId;
    private Long sourceArticleId;
    private Integer isAutoExtracted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;
}
