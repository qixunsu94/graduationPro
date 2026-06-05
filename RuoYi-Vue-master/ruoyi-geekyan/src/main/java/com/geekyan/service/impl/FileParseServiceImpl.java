package com.geekyan.service.impl;

import com.geekyan.service.IFileParseService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FileParseServiceImpl implements IFileParseService {

    private static final Logger log = LoggerFactory.getLogger(FileParseServiceImpl.class);

    @Override
    public Map<String, Object> parseFile(byte[] fileData, String fileName, String contentType) {
        String ext = getFileExtension(fileName).toLowerCase();
        Map<String, Object> result = new HashMap<>();

        try {
            String text;
            int pageCount = 0;

            switch (ext) {
                case "pdf":
                    Map<String, Object> pdfResult = parsePdf(fileData);
                    text = (String) pdfResult.get("text");
                    pageCount = (Integer) pdfResult.getOrDefault("pageCount", 0);
                    break;
                case "doc":
                    text = parseDoc(fileData);
                    break;
                case "docx":
                    text = parseDocx(fileData);
                    break;
                case "txt":
                case "md":
                default:
                    text = new String(fileData, StandardCharsets.UTF_8);
                    break;
            }

            result.put("text", text != null ? text : "");
            result.put("pageCount", pageCount);
            result.put("fileName", fileName);
            result.put("fileType", ext);
            result.put("fileSize", fileData.length);
            result.put("success", true);

        } catch (Exception e) {
            log.error("文件解析失败: fileName={}, error={}", fileName, e.getMessage());
            result.put("text", "");
            result.put("success", false);
            result.put("error", "文件解析失败: " + e.getMessage());
        }

        return result;
    }

    private Map<String, Object> parsePdf(byte[] fileData) throws Exception {
        Map<String, Object> result = new HashMap<>();
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(fileData))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            int pageCount = document.getNumberOfPages();
            result.put("text", text);
            result.put("pageCount", pageCount);
        }
        return result;
    }

    private String parseDoc(byte[] fileData) throws Exception {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(fileData))) {
            WordExtractor extractor = new WordExtractor(document);
            String text = extractor.getText();
            extractor.close();
            return text;
        }
    }

    private String parseDocx(byte[] fileData) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileData))) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : paragraphs) {
                String text = para.getText();
                if (text != null && !text.trim().isEmpty()) {
                    sb.append(text).append("\n");
                }
            }
            return sb.toString();
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "txt";
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}