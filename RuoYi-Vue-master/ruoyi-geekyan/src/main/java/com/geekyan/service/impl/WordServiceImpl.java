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
        return getOne(new LambdaQueryWrapper<Word>().eq(Word::getWord, word));
    }

    @Override
    public List<Word> fuzzySearch(String keyword, int limit) {
        return list(new LambdaQueryWrapper<Word>()
                .like(Word::getWord, keyword)
                .last("LIMIT " + limit));
    }
}
