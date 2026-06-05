package com.geekyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("learning_record")
public class LearningRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String recordType;
    private String subject;
    private String sourceType;
    private String sourceId;
    private Integer duration;
    private String contentSummary;
    private Integer score;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;
}
