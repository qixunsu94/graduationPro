package com.geekyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.TranslationCache;
import com.geekyan.mapper.TranslationCacheMapper;
import com.geekyan.service.ITranslationCacheService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TranslationCacheServiceImpl extends ServiceImpl<TranslationCacheMapper, TranslationCache> implements ITranslationCacheService {

    @Override
    public TranslationCache getByUserDocParagraph(Long userId, Long documentId, Integer paragraphIndex) {
        LambdaQueryWrapper<TranslationCache> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TranslationCache::getUserId, userId);
        wrapper.eq(TranslationCache::getDocumentId, documentId);
        wrapper.eq(TranslationCache::getParagraphIndex, paragraphIndex);
        return getOne(wrapper);
    }

    @Override
    public List<TranslationCache> getByUserDoc(Long userId, Long documentId) {
        LambdaQueryWrapper<TranslationCache> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TranslationCache::getUserId, userId);
        wrapper.eq(TranslationCache::getDocumentId, documentId);
        wrapper.orderByAsc(TranslationCache::getParagraphIndex);
        return list(wrapper);
    }

    @Override
    public void saveOrUpdateByParagraph(TranslationCache cache) {
        TranslationCache existing = getByUserDocParagraph(cache.getUserId(), cache.getDocumentId(), cache.getParagraphIndex());
        if (existing != null) {
            existing.setTranslatedText(cache.getTranslatedText());
            existing.setSourceLang(cache.getSourceLang());
            existing.setTargetLang(cache.getTargetLang());
            updateById(existing);
        } else {
            save(cache);
        }
    }
}
