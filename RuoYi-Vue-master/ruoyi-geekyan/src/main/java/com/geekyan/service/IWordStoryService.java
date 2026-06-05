package com.geekyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.geekyan.entity.WordStory;
import java.util.List;

public interface IWordStoryService extends IService<WordStory> {
    List<WordStory> getUserStories(Long userId, String bookName);
    WordStory createStory(Long userId, String title, String content, String words, String bookName, Integer wordCount, Integer totalWords);
    void deleteStory(Long userId, Long storyId);
}
