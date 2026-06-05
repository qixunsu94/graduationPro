package com.geekyan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.ReadingNote;
import com.geekyan.mapper.ReadingNoteMapper;
import com.geekyan.service.IReadingNoteService;
import com.geekyan.service.IReviewTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReadingNoteServiceImpl extends ServiceImpl<ReadingNoteMapper, ReadingNote> implements IReadingNoteService {

    @Autowired
    private IReviewTaskService reviewTaskService;

    @Override
    public boolean save(ReadingNote note) {
        boolean saved = super.save(note);
        if (saved) {
            // 笔记保存成功后，为其创建关联的复习任务
            String content = note.getSelectionText();
            if (content == null || content.trim().isEmpty()) {
                content = note.getContent();
                if (content != null && content.length() > 50) {
                    content = content.substring(0, 50) + "...";
                }
            }
            if (content != null && !content.trim().isEmpty()) {
                // 根据笔记类型推断学科和复习类型
                String subject = "english"; // 精读笔记默认英语
                String relatedType = "reading_note"; // 默认精读笔记
                if (note.getNoteType() != null) {
                    String type = note.getNoteType().toLowerCase();
                    if (type.contains("collect") || type.contains("bookmark") || type.contains("favorite")) {
                        relatedType = "reading_collect"; // 精读收藏
                    }
                }
                // 构建答案内容：笔记内容 + 高亮颜色标记
                String answerContent = note.getContent();
                if (note.getHighlightColor() != null && !note.getHighlightColor().isEmpty()) {
                    answerContent = "【高亮: " + note.getHighlightColor() + "】\n" + (answerContent != null ? answerContent : "");
                }
                reviewTaskService.createReviewTask(
                        note.getUserId(),
                        relatedType,
                        note.getId(),
                        content,
                        answerContent,
                        subject);
            }
        }
        return saved;
    }
}
