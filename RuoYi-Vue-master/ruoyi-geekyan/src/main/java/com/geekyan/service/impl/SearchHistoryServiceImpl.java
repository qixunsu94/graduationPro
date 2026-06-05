package com.geekyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geekyan.entity.SearchHistory;
import com.geekyan.mapper.SearchHistoryMapper;
import com.geekyan.service.ISearchHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SearchHistoryServiceImpl implements ISearchHistoryService {

    @Autowired
    private SearchHistoryMapper searchHistoryMapper;

    @Override
    public void addHistory(Long userId, String keyword, String queryType, String resultWord, String resultPhonetic,
            String resultMeaning) {
        LambdaQueryWrapper<SearchHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SearchHistory::getUserId, userId)
                .eq(SearchHistory::getKeyword, keyword)
                .last("LIMIT 1");
        SearchHistory existing = searchHistoryMapper.selectOne(wrapper);
        if (existing != null) {
            // 删除所有同keyword的旧记录，防止重复
            LambdaQueryWrapper<SearchHistory> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(SearchHistory::getUserId, userId)
                    .eq(SearchHistory::getKeyword, keyword);
            searchHistoryMapper.delete(deleteWrapper);
        }

        SearchHistory history = new SearchHistory();
        history.setUserId(userId);
        history.setKeyword(keyword);
        history.setQueryType(queryType);
        history.setResultWord(resultWord);
        history.setResultPhonetic(resultPhonetic);
        history.setResultMeaning(resultMeaning != null && resultMeaning.length() > 500 ? resultMeaning.substring(0, 500)
                : resultMeaning);
        history.setCreateTime(LocalDateTime.now());
        searchHistoryMapper.insert(history);

        LambdaQueryWrapper<SearchHistory> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(SearchHistory::getUserId, userId)
                .orderByAsc(SearchHistory::getCreateTime);
        List<SearchHistory> all = searchHistoryMapper.selectList(countWrapper);
        if (all.size() > 100) {
            for (int i = 0; i < all.size() - 100; i++) {
                searchHistoryMapper.deleteById(all.get(i).getId());
            }
        }
    }

    @Override
    public List<SearchHistory> getUserHistory(Long userId, int limit) {
        LambdaQueryWrapper<SearchHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SearchHistory::getUserId, userId)
                .orderByDesc(SearchHistory::getCreateTime)
                .last("LIMIT " + Math.min(limit, 50));
        return searchHistoryMapper.selectList(wrapper);
    }

    @Override
    public void clearUserHistory(Long userId) {
        LambdaQueryWrapper<SearchHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SearchHistory::getUserId, userId);
        searchHistoryMapper.delete(wrapper);
    }

    @Override
    public void deleteUserHistoryItem(Long userId, Long historyId) {
        LambdaQueryWrapper<SearchHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SearchHistory::getUserId, userId)
                .eq(SearchHistory::getId, historyId);
        searchHistoryMapper.delete(wrapper);
    }
}
