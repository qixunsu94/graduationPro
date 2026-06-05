package com.geekyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.SentenceAnalysisCache;
import com.geekyan.mapper.SentenceAnalysisCacheMapper;
import com.geekyan.service.ISentenceAnalysisCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class SentenceAnalysisCacheServiceImpl extends ServiceImpl<SentenceAnalysisCacheMapper, SentenceAnalysisCache>
        implements ISentenceAnalysisCacheService {

    private static final Logger log = LoggerFactory.getLogger(SentenceAnalysisCacheServiceImpl.class);

    @Override
    public SentenceAnalysisCache getByUserAndSentence(Long userId, String sentence) {
        String hash = md5(sentence.trim());
        return getByUserAndHash(userId, hash);
    }

    @Override
    public SentenceAnalysisCache getByUserAndHash(Long userId, String sentenceHash) {
        LambdaQueryWrapper<SentenceAnalysisCache> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SentenceAnalysisCache::getUserId, userId);
        wrapper.eq(SentenceAnalysisCache::getSentenceHash, sentenceHash);
        wrapper.last("LIMIT 1");
        return getOne(wrapper);
    }

    @Override
    public SentenceAnalysisCache saveCache(SentenceAnalysisCache cache) {
        if (cache.getSentence() == null || cache.getSentence().trim().isEmpty()) {
            throw new RuntimeException("sentence字段不能为空");
        }

        String trimmed = cache.getSentence().trim();
        String hash = md5(trimmed);
        cache.setSentence(trimmed);
        cache.setSentenceHash(hash);

        // 查是否已有缓存
        SentenceAnalysisCache existing = getByUserAndHash(cache.getUserId(), hash);
        if (existing != null) {
            // 已有缓存，更新内容
            existing.setFullAnalysis(cache.getFullAnalysis());
            existing.setSection1(cache.getSection1());
            existing.setSection2(cache.getSection2());
            existing.setSection3(cache.getSection3());
            existing.setSection4(cache.getSection4());
            existing.setTranslateLiteral(cache.getTranslateLiteral());
            existing.setTranslateFree(cache.getTranslateFree());
            existing.setAiModel(cache.getAiModel());
            updateById(existing);
            return existing;
        }

        // 新增缓存
        if (cache.getHitCount() == null) {
            cache.setHitCount(0);
        }
        save(cache);
        return cache;
    }

    @Override
    public void incrementHitCount(Long id) {
        try {
            LambdaUpdateWrapper<SentenceAnalysisCache> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(SentenceAnalysisCache::getId, id)
                    .setSql("hit_count = hit_count + 1");
            update(wrapper);
        } catch (Exception e) {
            log.warn("更新缓存命中次数失败: id={}", id);
        }
    }

    private String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            // 降级：用hashCode
            return String.valueOf(text.hashCode());
        }
    }
}
