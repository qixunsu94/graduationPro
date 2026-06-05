package com.geekyan.service;

import com.geekyan.entity.TranslationCache;
import java.util.List;

public interface ITranslationCacheService {
    TranslationCache getByUserDocParagraph(Long userId, Long documentId, Integer paragraphIndex);
    List<TranslationCache> getByUserDoc(Long userId, Long documentId);
    void saveOrUpdateByParagraph(TranslationCache cache);
}
