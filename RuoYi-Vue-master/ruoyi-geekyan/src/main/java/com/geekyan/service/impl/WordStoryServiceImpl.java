package com.geekyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.WordStory;
import com.geekyan.mapper.WordStoryMapper;
import com.geekyan.service.IWordStoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WordStoryServiceImpl extends ServiceImpl<WordStoryMapper, WordStory> implements IWordStoryService {

    @Override
    public List<WordStory> getUserStories(Long userId, String bookName) {
        LambdaQueryWrapper<WordStory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WordStory::getUserId, userId);
        if (bookName != null && !bookName.isEmpty()) {
            wrapper.and(w -> w.eq(WordStory::getBookName, bookName).or().isNull(WordStory::getBookName));
        }
        wrapper.orderByDesc(WordStory::getCreateTime);
        return list(wrapper);
    }

    @Override
    public WordStory createStory(Long userId, String title, String content, String words, String bookName,
            Integer wordCount, Integer totalWords) {
        WordStory story = new WordStory();
        story.setUserId(userId);
        story.setTitle(title);
        story.setContent(content);
        story.setWords(words);
        story.setBookName(bookName);
        story.setWordCount(wordCount);
        story.setTotalWords(totalWords);
        story.setCreateTime(LocalDateTime.now());
        story.setUpdateTime(LocalDateTime.now());
        save(story);
        return story;
    }

    @Override
    public void deleteStory(Long userId, Long storyId) {
        LambdaQueryWrapper<WordStory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WordStory::getUserId, userId)
                .eq(WordStory::getId, storyId);
        remove(wrapper);
    }
}
