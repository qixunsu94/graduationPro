package com.geekyan.service;

import java.util.List;
import java.util.Map;

public interface IOfflineDictService {
    Map<String, Object> lookupWord(String word);

    Map<String, Object> lookupWord(String word, String dictName);

    List<Map<String, Object>> fuzzySuggest(String keyword, int limit);

    List<Map<String, Object>> reverseSearch(String chinese, int limit);

    List<Map<String, Object>> listDicts();

    byte[] getAudio(String filePath);

    byte[] getAudio(String dictName, String filePath);

    byte[] getStaticResource(String filePath);
}
