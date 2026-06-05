package com.geekyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 精读句子解析缓存表
 * 用户在精读过程中点击"解析"按钮时，AI返回的4段式解析结果会缓存到此表。
 * 同一用户对同一句子的重复解析请求直接返回缓存，避免重复调用AI接口。
 */
@Data
@TableName("sentence_analysis_cache")
public class SentenceAnalysisCache {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 英文原文句子（用于匹配缓存） */
    private String sentence;

    /** 句子MD5哈希（用于快速索引匹配） */
    private String sentenceHash;

    /** AI完整解析原文 */
    private String fullAnalysis;

    /** 第1段：原句重现与整体感知 */
    private String section1;

    /** 第2段：核心词汇与地道表达 */
    private String section2;

    /** 第3段：语法名词剖析 */
    private String section3;

    /** 第4段：深度解析与考点提示 */
    private String section4;

    /** 直译 */
    private String translateLiteral;

    /** 意译 */
    private String translateFree;

    /** AI模型标识（用于后续模型升级时区分缓存来源） */
    private String aiModel;

    /** 缓存命中次数 */
    private Integer hitCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
