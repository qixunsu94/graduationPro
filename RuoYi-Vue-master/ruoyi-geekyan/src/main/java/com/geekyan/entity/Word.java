package com.geekyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("word")
public class Word {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String word;
    private String phonetic;
    private String meanings;
    private String examples;
    private Integer frequency;
    private String difficulty;
    private String etymology;
    private String synonyms;
    private String antonyms;
    private String wordForms;
    private String examTags;
    private String frequencyLevel;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;
}
