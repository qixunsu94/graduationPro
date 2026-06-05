package com.geekyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("review_task")
public class ReviewTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String relatedType;
    private Long relatedId;
    private String content;
    private String answerContent;
    private String subject;
    private Integer reviewStage;
    private Integer reviewCount;
    private LocalDateTime nextReviewTime;
    private LocalDateTime lastReviewTime;
    private Integer isCompleted;
    private Integer accuracyScore;
    private Double masteryLevel;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;
}
