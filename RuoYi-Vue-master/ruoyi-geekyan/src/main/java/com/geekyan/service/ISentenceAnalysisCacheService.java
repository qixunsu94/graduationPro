package com.geekyan.service;

import com.geekyan.entity.SentenceAnalysisCache;

public interface ISentenceAnalysisCacheService {

    /**
     * 根据用户ID和句子内容查询缓存
     */
    SentenceAnalysisCache getByUserAndSentence(Long userId, String sentence);

    /**
     * 根据用户ID和句子哈希查询缓存
     */
    SentenceAnalysisCache getByUserAndHash(Long userId, String sentenceHash);

    /**
     * 保存或更新缓存（同一用户同一句子不重复保存）
     */
    SentenceAnalysisCache saveCache(SentenceAnalysisCache cache);

    /**
     * 缓存命中时更新命中次数
     */
    void incrementHitCount(Long id);
}
