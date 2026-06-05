package com.geekyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.AiNote;
import com.geekyan.entity.LongSentence;
import com.geekyan.entity.PdfDocument;
import com.geekyan.entity.ReadingNote;
import com.geekyan.mapper.AiNoteMapper;
import com.geekyan.mapper.LongSentenceMapper;
import com.geekyan.mapper.PdfDocumentMapper;
import com.geekyan.mapper.ReadingNoteMapper;
import com.geekyan.service.IPdfDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PdfDocumentServiceImpl extends ServiceImpl<PdfDocumentMapper, PdfDocument> implements IPdfDocumentService {

    private static final Logger log = LoggerFactory.getLogger(PdfDocumentServiceImpl.class);

    @Autowired
    private ReadingNoteMapper readingNoteMapper;

    @Autowired
    private LongSentenceMapper longSentenceMapper;

    @Autowired
    private AiNoteMapper aiNoteMapper;

    @Override
    public List<PdfDocument> getUserDocuments(Long userId) {
        LambdaQueryWrapper<PdfDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PdfDocument::getUserId, userId);
        wrapper.orderByDesc(PdfDocument::getCreateTime);
        return list(wrapper);
    }

    @Override
    public List<PdfDocument> getUserDocumentsByType(Long userId, List<String> fileTypes) {
        LambdaQueryWrapper<PdfDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PdfDocument::getUserId, userId);
        if (fileTypes != null && !fileTypes.isEmpty()) {
            wrapper.in(PdfDocument::getFileType, fileTypes);
        }
        wrapper.orderByDesc(PdfDocument::getCreateTime);
        return list(wrapper);
    }

    @Override
    public PdfDocument addDocument(Long userId, PdfDocument doc) {
        doc.setUserId(userId);
        doc.setStatus(1);
        String parsedContent = doc.getParsedContent();
        log.info("添加文档: fileName={}, parsedContent长度={}", doc.getFileName(),
                parsedContent != null ? parsedContent.length() : 0);
        save(doc);
        if (parsedContent != null && !parsedContent.isEmpty()) {
            PdfDocument update = new PdfDocument();
            update.setId(doc.getId());
            update.setParsedContent(parsedContent);
            updateById(update);
            doc.setParsedContent(parsedContent);
        }
        return doc;
    }

    @Override
    public boolean deleteDocument(Long userId, Long docId) {
        // 先查出文档获取文件名，用于清理关联数据
        PdfDocument doc = getOne(new LambdaQueryWrapper<PdfDocument>()
                .eq(PdfDocument::getId, docId)
                .eq(PdfDocument::getUserId, userId));
        if (doc == null) {
            return false;
        }
        String fileName = doc.getFileName();

        // 删除关联的阅读笔记(高亮/笔记)
        int notesDeleted = readingNoteMapper.delete(new LambdaQueryWrapper<ReadingNote>()
                .eq(ReadingNote::getDocumentId, docId)
                .eq(ReadingNote::getUserId, userId));
        log.info("删除文档关联笔记: docId={}, count={}", docId, notesDeleted);

        // 删除关联的长难句(按来源文件名匹配)
        int sentencesDeleted = longSentenceMapper.delete(new LambdaQueryWrapper<LongSentence>()
                .eq(LongSentence::getUserId, userId)
                .eq(LongSentence::getSource, fileName));
        log.info("删除文档关联长难句: docId={}, fileName={}, count={}", docId, fileName, sentencesDeleted);

        // 删除关联的AI笔记
        int aiNotesDeleted = aiNoteMapper.delete(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getSourceArticleId, docId)
                .eq(AiNote::getUserId, userId));
        log.info("删除文档关联AI笔记: docId={}, count={}", docId, aiNotesDeleted);

        // 删除文档本身
        return remove(new LambdaQueryWrapper<PdfDocument>()
                .eq(PdfDocument::getId, docId)
                .eq(PdfDocument::getUserId, userId));
    }

    @Override
    public PdfDocument getDocumentById(Long userId, Long docId) {
        LambdaQueryWrapper<PdfDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PdfDocument::getId, docId);
        wrapper.eq(PdfDocument::getUserId, userId);
        return getOne(wrapper);
    }

    @Override
    public PdfDocument getDocumentByFileName(Long userId, String fileName) {
        LambdaQueryWrapper<PdfDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PdfDocument::getUserId, userId);
        wrapper.eq(PdfDocument::getFileName, fileName);
        return getOne(wrapper);
    }
}
