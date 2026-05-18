package com.geekyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.WordBook;
import com.geekyan.mapper.WordBookMapper;
import com.geekyan.service.IWordBookService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WordBookServiceImpl extends ServiceImpl<WordBookMapper, WordBook> implements IWordBookService {

    @Override
    public List<WordBook> getUserWordBook(Long userId, String bookName) {
        LambdaQueryWrapper<WordBook> wrapper = new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId);
        if (bookName != null && !bookName.isEmpty()) {
            wrapper.eq(WordBook::getBookName, bookName);
        }
        return list(wrapper.orderByDesc(WordBook::getCreateTime));
    }

    @Override
    public void addWord(Long userId, Long wordId, String word, String bookName) {
        WordBook wb = new WordBook();
        wb.setUserId(userId);
        wb.setWordId(wordId);
        wb.setWord(word);
        wb.setBookName(bookName != null ? bookName : "默认生词本");
        wb.setMasteryLevel(0);
        wb.setReviewCount(0);
        save(wb);
    }

    @Override
    public void removeWord(Long userId, Long wordId, String bookName) {
        remove(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .eq(WordBook::getWordId, wordId)
                .eq(WordBook::getBookName, bookName != null ? bookName : "默认生词本"));
    }
}
