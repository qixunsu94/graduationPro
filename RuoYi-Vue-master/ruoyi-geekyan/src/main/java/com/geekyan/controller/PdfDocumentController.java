package com.geekyan.controller;

import com.geekyan.entity.PdfDocument;
import com.geekyan.service.IFileParseService;
import com.geekyan.service.IPdfDocumentService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/geekyan/pdf")
public class PdfDocumentController extends BaseController {

    private static final List<String> PDF_EXTENSIONS = Arrays.asList("pdf");

    @Autowired
    private IPdfDocumentService pdfDocumentService;

    @Autowired
    private IFileParseService fileParseService;

    @GetMapping("/list")
    public TableDataInfo list() {
        startPage();
        List<PdfDocument> list = pdfDocumentService.getUserDocumentsByType(getUserId(), PDF_EXTENSIONS);
        return getDataTable(list);
    }

    @PostMapping
    public AjaxResult add(@RequestBody PdfDocument doc) {
        PdfDocument saved = pdfDocumentService.addDocument(getUserId(), doc);
        return success(saved);
    }

    @PostMapping("/upload")
    public AjaxResult upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return error("请选择文件");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            return error("文件名不能为空");
        }

        int dotIndex = fileName.lastIndexOf(".");
        String ext = dotIndex >= 0 ? fileName.substring(dotIndex + 1).toLowerCase() : "";
        if (!PDF_EXTENSIONS.contains(ext)) {
            return error("仅支持 PDF 文件，TXT/MD文件请使用 /geekyan/txt/upload 接口");
        }

        try {
            byte[] fileData = file.getBytes();
            Map<String, Object> parseResult = fileParseService.parseFile(fileData, fileName, file.getContentType());

            if (!Boolean.TRUE.equals(parseResult.get("success"))) {
                return error((String) parseResult.getOrDefault("error", "文件解析失败"));
            }

            String parsedContent = (String) parseResult.get("text");
            int pageCount = (Integer) parseResult.getOrDefault("pageCount", 0);

            PdfDocument doc = new PdfDocument();
            doc.setFileName(fileName);
            doc.setFileSize(file.getSize());
            doc.setFileType(ext);
            doc.setPageCount(pageCount);
            doc.setParsedContent(parsedContent);

            PdfDocument saved = pdfDocumentService.addDocument(getUserId(), doc);

            Map<String, Object> data = new java.util.HashMap<>();
            data.put("id", saved.getId());
            data.put("fileName", saved.getFileName());
            data.put("fileType", saved.getFileType());
            data.put("fileSize", saved.getFileSize());
            data.put("pageCount", saved.getPageCount());
            data.put("hasContent", parsedContent != null && !parsedContent.isEmpty());

            return success(data);
        } catch (Exception e) {
            return error("文件上传失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        return toAjax(pdfDocumentService.deleteDocument(getUserId(), id));
    }

    @GetMapping("/content/{id}")
    public AjaxResult getContent(@PathVariable Long id) {
        PdfDocument doc = pdfDocumentService.getDocumentById(getUserId(), id);
        if (doc != null) {
            return success(doc);
        }
        return error("文档不存在");
    }

    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable Long id, @RequestBody PdfDocument doc) {
        doc.setId(id);
        doc.setUserId(getUserId());
        return toAjax(pdfDocumentService.updateById(doc));
    }
}
