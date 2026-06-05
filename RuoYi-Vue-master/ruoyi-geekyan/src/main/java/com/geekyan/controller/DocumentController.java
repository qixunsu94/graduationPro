package com.geekyan.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.geekyan.entity.PdfDocument;
import com.geekyan.service.IPdfDocumentService;
import com.geekyan.service.IAiService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/geekyan/document")
public class DocumentController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    @Autowired
    private IPdfDocumentService pdfDocumentService;

    @Autowired
    private IAiService aiService;

    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String fileType) {
        startPage();
        List<PdfDocument> list;
        if (fileType != null && !fileType.isEmpty()) {
            list = pdfDocumentService.getUserDocumentsByType(getUserId(), List.of(fileType.split(",")));
        } else {
            list = pdfDocumentService.getUserDocuments(getUserId());
        }
        return getDataTable(list);
    }

    @PostMapping
    public AjaxResult add(@RequestBody PdfDocument doc) {
        PdfDocument saved = pdfDocumentService.addDocument(getUserId(), doc);
        return success(saved);
    }

    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        return toAjax(pdfDocumentService.deleteDocument(getUserId(), id));
    }

    @GetMapping("/content/{id}")
    public AjaxResult getContent(@PathVariable String id) {
        PdfDocument doc = null;
        try {
            Long docId = Long.parseLong(id);
            doc = pdfDocumentService.getDocumentById(getUserId(), docId);
        } catch (NumberFormatException e) {
            // 非数字ID，按文件名查找
            doc = pdfDocumentService.getDocumentByFileName(getUserId(), id);
        }
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

    @PostMapping("/speech-to-text")
    public AjaxResult speechToText(@RequestBody Map<String, Object> params) {
        String audioBase64 = (String) params.get("audio_base64");
        if (audioBase64 == null || audioBase64.isEmpty()) {
            return error("请提供音频数据");
        }
        String language = (String) params.getOrDefault("language", "zh");

        try {
            String prompt = "请将以下语音内容转换为文字。语言：" + language + "\n" +
                    "严格返回JSON格式（不要markdown代码块）：\n" +
                    "{\"text\":\"识别的文字内容\",\"language\":\"识别的语言\",\"confidence\":0.95}\n" +
                    "注意：由于你无法直接处理音频，请根据用户可能提供的上下文返回空结果。只返回JSON。";

            String aiResult = aiService.chat(
                    "你是语音识别助手。用户会上传音频数据，你需要将其转为文字。",
                    prompt, "speech-to-text-" + getUserId());

            if (aiResult != null && !aiResult.isEmpty()) {
                String cleaned = aiResult.trim();
                if (cleaned.startsWith("```")) {
                    cleaned = cleaned.replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "").replaceAll("\\s*```$",
                            "");
                }
                try {
                    JSONObject parsed = JSON.parseObject(cleaned);
                    if (parsed != null) {
                        return success(parsed);
                    }
                } catch (Exception e) {
                    log.warn("解析语音识别结果失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("语音转文字失败: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("text", "");
        result.put("language", language);
        result.put("confidence", 0.0);
        result.put("message", "语音识别服务暂不可用，请稍后重试");
        return success(result);
    }

    @PostMapping("/split-sentences/{id}")
    public AjaxResult splitSentences(@PathVariable Long id) {
        PdfDocument doc = pdfDocumentService.getDocumentById(getUserId(), id);
        if (doc == null) {
            return error("文档不存在");
        }
        String content = doc.getParsedContent();
        if (content == null || content.isEmpty()) {
            return error("文档内容为空");
        }

        if (content.length() > 5000) {
            content = content.substring(0, 5000);
        }

        try {
            String prompt = "请将以下英文文档内容按句子进行断句分割。\n" +
                    "严格返回JSON格式（不要markdown代码块）：\n" +
                    "{\"sentences\":[{\"index\":0,\"text\":\"句子内容\",\"paragraph\":0}]}\n\n" +
                    "要求：\n1. 按英文句号、问号、感叹号断句\n2. 保留段落信息\n3. 每个句子给出序号\n4. 只返回JSON\n\n" +
                    "文档内容：\n" + content;

            String aiResult = aiService.chat(
                    "你是英语文本处理专家，擅长对英文文档进行精确断句。",
                    prompt, "split-sentences-" + id);

            if (aiResult != null && !aiResult.isEmpty() && !aiResult.contains("unavailable")) {
                String cleaned = aiResult.trim();
                if (cleaned.startsWith("```")) {
                    cleaned = cleaned.replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "").replaceAll("\\s*```$",
                            "");
                }
                try {
                    JSONObject parsed = JSON.parseObject(cleaned);
                    if (parsed != null && parsed.containsKey("sentences")) {
                        return success(parsed);
                    }
                } catch (Exception e) {
                    log.warn("解析断句结果失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("AI断句失败: {}", e.getMessage());
        }

        List<Map<String, Object>> sentences = new ArrayList<>();
        String[] rawSentences = content.split("(?<=[.!?])\\s+");
        for (int i = 0; i < rawSentences.length; i++) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("index", i);
            entry.put("text", rawSentences[i].trim());
            entry.put("paragraph", 0);
            sentences.add(entry);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("sentences", sentences);
        return success(result);
    }
}
