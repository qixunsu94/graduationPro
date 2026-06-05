package com.geekyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("reading_note")
public class ReadingNote {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long documentId;
    private Integer paragraphIndex;
    private String sentence;
    private String content;
    private String sourceFile;
    private String noteType;
    private String highlightColor;
    private String knowledgePoints;
    private String selectionText;
    private Integer selectionStart;
    private Integer selectionEnd;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;
}
