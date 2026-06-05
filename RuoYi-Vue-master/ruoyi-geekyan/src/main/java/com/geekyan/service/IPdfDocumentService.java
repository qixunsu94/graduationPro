package com.geekyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.geekyan.entity.PdfDocument;

import java.util.List;

public interface IPdfDocumentService extends IService<PdfDocument> {

    List<PdfDocument> getUserDocuments(Long userId);

    List<PdfDocument> getUserDocumentsByType(Long userId, List<String> fileTypes);

    PdfDocument addDocument(Long userId, PdfDocument doc);

    boolean deleteDocument(Long userId, Long docId);

    PdfDocument getDocumentById(Long userId, Long docId);

    PdfDocument getDocumentByFileName(Long userId, String fileName);
}
