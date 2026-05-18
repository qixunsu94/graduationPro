package com.geekyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.geekyan.entity.Word;
import java.util.List;

public interface IWordService extends IService<Word> {
    Word searchWord(String word);
    List<Word> fuzzySearch(String keyword, int limit);
}
