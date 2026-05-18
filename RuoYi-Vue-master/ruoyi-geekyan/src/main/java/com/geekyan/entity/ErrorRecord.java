package com.geekyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("error_record")
public class ErrorRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String subject;
    private String question;
    private String userAnswer;
    private String correctAnswer;
    private String analysis;
    private String knowledgePoints;
    private String learnCard;
    private String source;
    private String sourceId;
    private Integer masteryLevel;
    private Integer reviewCount;
    private LocalDateTime nextReviewTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;
}
