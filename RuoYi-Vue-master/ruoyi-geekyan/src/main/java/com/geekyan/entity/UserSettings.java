package com.geekyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_settings")
public class UserSettings {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer autoPlayingVoice;
    private Integer autoTextShadow;
    private Integer autoPronunciation;
    private String playingVoiceSpeed;
    private String speechRoleName;
    private String speechRoleNameLabel;
    private String targetLanguage;
    private String aiProvider;

    private Integer targetYear;
    private String targetSchool;
    private String targetMajor;
    private Integer targetScore;
    private String examSubjects;

    private Integer autoAddWord;
    private String defaultDict;
    private Integer dailyWordGoal;
    private Integer dailyQuestionGoal;
    private String readingMode;
    private Integer pushEnabled;
    private String pushTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;
}
