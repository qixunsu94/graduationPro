package com.geekyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.geekyan.entity.WordBook;
import java.util.List;

public interface IWordBookService extends IService<WordBook> {
    List<WordBook> getUserWordBook(Long userId, String bookName);
    void addWord(Long userId, Long wordId, String word, String bookName);
    void removeWord(Long userId, Long wordId, String bookName);
}
