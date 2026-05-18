package com.geekyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_collect")
public class UserCollect {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private String content;
    private String translation;
    private String messageId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;
}
