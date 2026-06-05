package com.geekyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("translation_cache")
public class TranslationCache {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long documentId;
    private Integer paragraphIndex;
    private String sourceText;
    private String translatedText;
    private String sourceLang;
    private String targetLang;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
