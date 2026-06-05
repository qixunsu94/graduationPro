package com.geekyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.geekyan.entity.LongSentence;

import java.util.List;
import java.util.Map;

public interface ILongSentenceService extends IService<LongSentence> {

    List<LongSentence> getUserSentences(Long userId, String difficulty, String sentenceType);

    LongSentence saveSentence(Long userId, LongSentence sentence);

    Map<String, Object> generateSimilarSentences(LongSentence sentence);

    Map<String, Object> generateParagraphFromSentences(Long userId, List<Long> sentenceIds);
}
