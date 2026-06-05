package com.geekyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("long_sentence")
public class LongSentence {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String sentence;
    private String translation;
    private String literalTranslation;
    private String freeTranslation;
    private String analysis;
    private String source;
    private String sentenceType;
    private String difficulty;
    private String grammarTags;
    private String coreVocab;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;
}
