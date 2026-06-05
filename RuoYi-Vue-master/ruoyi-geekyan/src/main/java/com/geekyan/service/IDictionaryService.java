package com.geekyan.service;

import com.geekyan.entity.Word;

import java.util.List;
import java.util.Map;

public interface IDictionaryService {
    Word lookupWord(String word);
    List<Map<String, Object>> fuzzySuggest(String keyword, int limit);
}
