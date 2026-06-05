package com.geekyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.Word;
import com.geekyan.mapper.WordMapper;
import com.geekyan.service.IWordService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WordServiceImpl extends ServiceImpl<WordMapper, Word> implements IWordService {

    @Override
    public Word searchWord(String word) {
        Word result = getOne(new LambdaQueryWrapper<Word>().eq(Word::getWord, word.toLowerCase()));
        if (result != null && result.getMeanings() != null
                && (result.getMeanings().contains("unavailable") || result.getMeanings().contains("AI service"))) {
            removeById(result.getId());
            return null;
        }
        return result;
    }

    @Override
    public List<Word> fuzzySearch(String keyword, int limit) {
        return list(new LambdaQueryWrapper<Word>()
                .likeRight(Word::getWord, keyword)
                .last("LIMIT " + limit));
    }
}
