package com.geekyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("search_history")
public class SearchHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String keyword;
    /** 查询类型：EN_WORD_TO_ZH / EN_PHRASE_TO_ZH / EN_SENTENCE_TO_ZH / ZH_WORD_TO_EN / ZH_PHRASE_TO_EN / ZH_SENTENCE_TO_EN */
    private String queryType;
    private String resultWord;
    private String resultPhonetic;
    private String resultMeaning;
    private String resultExample;
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
}
