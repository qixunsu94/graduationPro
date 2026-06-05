package com.geekyan.service;

import com.geekyan.entity.SearchHistory;
import java.util.List;

public interface ISearchHistoryService {
    void addHistory(Long userId, String keyword, String queryType, String resultWord, String resultPhonetic,
            String resultMeaning);

    List<SearchHistory> getUserHistory(Long userId, int limit);

    void clearUserHistory(Long userId);

    void deleteUserHistoryItem(Long userId, Long historyId);
}
