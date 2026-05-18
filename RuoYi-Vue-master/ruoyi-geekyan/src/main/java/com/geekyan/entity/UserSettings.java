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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer delFlag;
}
