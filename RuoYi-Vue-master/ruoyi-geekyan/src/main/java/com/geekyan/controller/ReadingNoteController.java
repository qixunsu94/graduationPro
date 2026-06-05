package com.geekyan.controller;

import com.geekyan.entity.PdfDocument;
import com.geekyan.entity.ReadingNote;
import com.geekyan.service.IAiService;
import com.geekyan.service.IPdfDocumentService;
import com.geekyan.service.IReadingNoteService;
import com.geekyan.util.AiTextCleaner;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson2.JSON;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/geekyan/reading-note")
public class ReadingNoteController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(ReadingNoteController.class);

    @Value("${geekyan.upload.path:/tmp/geekyan/uploads}")
    private String uploadBasePath;

    @Value("${geekyan.upload.url-prefix:/uploads}")
    private String uploadUrlPrefix;

    @Autowired
    private IReadingNoteService readingNoteService;

    @Autowired
    private IPdfDocumentService pdfDocumentService;

    @Autowired
    private IAiService aiService;

    /** 精读AI助手专属Prompt */
    private static final String READING_ANALYSIS_PROMPT = "你是一位专业的考研英语语法和文体分析专家，同时精通地道英语表达和精准翻译。你的任务是对用户在精读过程中划选的文本进行专业分析。\n\n"
            +
            "你具备以下核心能力：\n" +
            "1. 语法结构深度拆解：能准确识别各种从句、非谓语动词、特殊句式\n" +
            "2. 地道表达提取：能识别熟词僻义、固定搭配、短语动词、习语\n" +
            "3. 精准翻译：能提供直译和意译两种翻译方式\n" +
            "4. 考研考点关联：能将句子分析与考研英语考点联系起来\n\n" +
            "重要规则：\n" +
            "- 你必须基于提供的文章上下文来理解划选文本的含义\n" +
            "- 所有数学符号和公式用纯文本描述，禁止使用LaTeX语法\n" +
            "- 不要在回答中提及\"协议\"、\"模板\"等内部术语\n" +
            "- 回答要精炼高效，不说废话，但关键分析必须充分展开";

    /**
     * 精读AI助手专属接口 - 分析划选文本
     * 与通用AI对话模块完全分离，专门服务于精读场景
     */
    @PostMapping("/analyze-selection")
    public AjaxResult analyzeSelection(@RequestBody Map<String, Object> params) {
        Long documentId = params.get("documentId") != null ? Long.parseLong(params.get("documentId").toString()) : null;
        String selectedText = (String) params.get("selectedText");
        String analysisType = (String) params.getOrDefault("analysisType", "grammar");
        String userMessage = (String) params.get("message");

        if (selectedText == null || selectedText.trim().isEmpty()) {
            return error("请提供要分析的文本");
        }

        // 1. 获取文章上下文
        String articleContext = "";
        String articleTitle = "";
        if (documentId != null) {
            try {
                PdfDocument doc = pdfDocumentService.getDocumentById(getUserId(), documentId);
                if (doc != null && doc.getParsedContent() != null) {
                    articleTitle = doc.getFileName() != null ? doc.getFileName() : "";
                    String content = doc.getParsedContent();
                    // 截取上下文，避免过长
                    if (content.length() > 3000) {
                        // 尝试定位选中文本在文章中的位置，取其前后文
                        int idx = content.indexOf(selectedText.trim());
                        if (idx >= 0) {
                            int start = Math.max(0, idx - 1000);
                            int end = Math.min(content.length(), idx + selectedText.length() + 1000);
                            articleContext = content.substring(start, end);
                            if (start > 0)
                                articleContext = "..." + articleContext;
                            if (end < content.length())
                                articleContext = articleContext + "...";
                        } else {
                            articleContext = content.substring(0, 3000) + "...";
                        }
                    } else {
                        articleContext = content;
                    }
                }
            } catch (Exception e) {
                log.warn("获取文章上下文失败: {}", e.getMessage());
            }
        }

        // 2. 根据分析类型构造专业Prompt
        String userPrompt;
        switch (analysisType) {
            case "grammar":
                userPrompt = buildGrammarPrompt(selectedText, articleContext, articleTitle);
                break;
            case "translate":
                userPrompt = buildTranslatePrompt(selectedText, articleContext, articleTitle);
                break;
            case "word":
                userPrompt = buildWordPrompt(selectedText, articleContext, articleTitle);
                break;
            case "sentence":
                userPrompt = buildSentenceAnalysisPrompt(selectedText, articleContext, articleTitle);
                break;
            case "chat":
                // 自由对话模式，附带文章上下文
                userPrompt = buildChatPrompt(selectedText, articleContext, articleTitle, userMessage);
                break;
            default:
                userPrompt = buildGrammarPrompt(selectedText, articleContext, articleTitle);
        }

        // 3. 调用底层AI通信方法（使用chatWithoutHistory避免污染通用对话历史）
        String aiReply = aiService.chatWithoutHistory(READING_ANALYSIS_PROMPT, userPrompt);

        if (aiReply == null || aiReply.isEmpty()) {
            return error("AI 服务暂时不可用，请稍后重试");
        }
        String cleanedAiReply = AiTextCleaner.clean(aiReply);

        // 4. 将分析结果自动保存为笔记（记录学习轨迹）
        try {
            ReadingNote note = new ReadingNote();
            note.setUserId(getUserId());
            note.setDocumentId(documentId);
            note.setSourceFile(articleTitle);
            note.setSelectionText(selectedText);
            note.setSentence(selectedText);
            note.setContent(cleanedAiReply);
            note.setNoteType(analysisType);
            note.setCreateTime(java.time.LocalDateTime.now());
            note.setUpdateTime(java.time.LocalDateTime.now());
            readingNoteService.save(note);
        } catch (Exception e) {
            log.error("自动保存精读笔记失败", e);
            // 保存失败不影响结果返回
        }

        // 5. 优化返回结构：尝试将AI返回的JSON字符串解析为对象
        Object replyObject;
        try {
            replyObject = JSON.parseObject(aiReply, Map.class);
        } catch (Exception e) {
            try {
                replyObject = JSON.parseObject(cleanedAiReply, Map.class);
            } catch (Exception ignored) {
                // AI返回的不是合法JSON，直接返回清洗后的字符串
                replyObject = cleanedAiReply;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", replyObject);
        result.put("content", cleanedAiReply);
        result.put("analysisType", analysisType);
        return success(result);
    }

    /** 构造语法分析Prompt */
    private String buildGrammarPrompt(String selectedText, String context, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对以下英语句子进行深度语法解析，严格按照4段式输出，每段用【】标记开头：\n\n");

        if (!title.isEmpty())
            sb.append("文章标题：").append(title).append("\n");
        if (!context.isEmpty())
            sb.append("文章上下文：\n").append(context).append("\n\n");

        sb.append("用户划选的句子：\n").append(selectedText).append("\n\n");

        sb.append("【第1段：原句重现与整体感知】\n");
        sb.append("展示原句，然后用一句话概括句子的核心意思和表达功能。\n\n");

        sb.append("【第2段：核心词汇与地道表达】\n");
        sb.append("从句子中提取3-8个核心词汇或地道表达。提取标准：考研高频词汇、熟词僻义、固定搭配、短语动词、习语。\n");
        sb.append("每个词汇展示：拼写、音标、在当前句中的词性、在当前句中的具体含义。\n");
        sb.append("如果是熟词僻义标注\"熟词僻义\"，如果是固定搭配标注完整形式。格式：word /phonetic/ (pos) - 句中含义\n\n");

        sb.append("【第3段：语法名词剖析】\n");
        sb.append("以缩进层级结构展示句子的语法成分，从句子整体到从句再到短语逐层拆解。标注内容包括：\n");
        sb.append("- 句子整体类型：简单句/并列句/复合句\n");
        sb.append("- 主句成分：主语、谓语、宾语、补语、表语\n");
        sb.append("- 从句：从句类型、引导词、从句内部主谓宾\n");
        sb.append("- 非谓语动词：类型、语法功能、逻辑主语\n");
        sb.append("- 特殊句式：虚拟语气/倒装句/强调句/省略句\n");
        sb.append("- 时态与语态\n");
        sb.append("- 修饰关系：用缩进表示修饰关系\n\n");

        sb.append("【第4段：深度解析与考点提示】\n");
        sb.append("- 句子结构特点\n");
        sb.append("- 翻译技巧（直译和意译各给出一种）\n");
        sb.append("- 考研考点关联\n");
        sb.append("- 理解难点突破");

        return sb.toString();
    }

    /** 构造翻译Prompt */
    private String buildTranslatePrompt(String selectedText, String context, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("请将以下英语文本翻译成中文，要求翻译精准、自然流畅。\n\n");

        if (!title.isEmpty())
            sb.append("文章标题：").append(title).append("\n");
        if (!context.isEmpty())
            sb.append("文章上下文：\n").append(context).append("\n\n");

        sb.append("需要翻译的文本：\n").append(selectedText).append("\n\n");
        sb.append("请提供：\n");
        sb.append("1. 直译：尽可能保留原文的语法结构和词语含义\n");
        sb.append("2. 意译：用自然流畅的中文表达原文的含义\n");
        sb.append("3. 翻译难点：指出翻译中需要注意的难点和技巧");

        return sb.toString();
    }

    /** 构造单词查询Prompt */
    private String buildWordPrompt(String selectedText, String context, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("请详细解析以下英语单词/短语，结合文章上下文给出精准释义。\n\n");

        if (!title.isEmpty())
            sb.append("文章标题：").append(title).append("\n");
        if (!context.isEmpty())
            sb.append("文章上下文：\n").append(context).append("\n\n");

        sb.append("需要解析的单词/短语：").append(selectedText).append("\n\n");
        sb.append("请按以下JSON格式输出（不要输出其他内容）：\n");
        sb.append(
                "{\"word\":\"单词\",\"phonetic\":\"音标\",\"meanings\":[{\"pos\":\"词性\",\"def\":\"释义\",\"contextMeaning\":\"在当前句中的具体含义\"}],");
        sb.append("\"examples\":[{\"en\":\"英文例句\",\"cn\":\"中文翻译\"}],");
        sb.append("\"forms\":[{\"name\":\"词形变化名\",\"value\":\"变化形式\"}],");
        sb.append("\"collocations\":[\"常见搭配1\",\"常见搭配2\"]}\n\n");
        sb.append("要求：\n");
        sb.append("1. contextMeaning必须基于文章上下文给出该词在当前语境下的具体含义\n");
        sb.append("2. 如果是熟词僻义，请特别标注\n");
        sb.append("3. 给出2-3个常见搭配\n");
        sb.append("4. 只输出JSON");

        return sb.toString();
    }

    /** 构造句子分析Prompt（简版，用于快速查看） */
    private String buildSentenceAnalysisPrompt(String selectedText, String context, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下英语句子，给出简明的语法分析和翻译。\n\n");

        if (!title.isEmpty())
            sb.append("文章标题：").append(title).append("\n");
        if (!context.isEmpty())
            sb.append("文章上下文：\n").append(context).append("\n\n");

        sb.append("句子：").append(selectedText).append("\n\n");
        sb.append("请按以下JSON格式输出（不要输出其他内容）：\n");
        sb.append("{\"translation\":\"中文翻译\",\"vocab\":[{\"word\":\"英文单词\",\"pos\":\"词性\",\"meaning\":\"中文释义\"}],");
        sb.append(
                "\"grammar\":{\"type\":\"句子类型\",\"mainClause\":\"主句结构\",\"subClauses\":[{\"type\":\"从句类型\",\"content\":\"从句内容\"}]}}\n\n");
        sb.append("要求：\n1. translation给出自然流畅的中文翻译\n2. vocab列出3-5个重点词汇\n3. grammar简要分析句子结构\n4. 只输出JSON");

        return sb.toString();
    }

    /** 构造自由对话Prompt（附带文章上下文） */
    private String buildChatPrompt(String selectedText, String context, String title, String userMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("学生正在精读一篇英语文章，提出了以下问题。请基于文章上下文给出专业解答。\n\n");

        if (!title.isEmpty())
            sb.append("文章标题：").append(title).append("\n");
        if (!context.isEmpty())
            sb.append("文章上下文：\n").append(context).append("\n\n");
        if (selectedText != null && !selectedText.isEmpty()) {
            sb.append("当前关注的文本：").append(selectedText).append("\n\n");
        }

        sb.append("学生提问：").append(userMessage != null ? userMessage : "");

        return sb.toString();
    }

    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) Long documentId,
            @RequestParam(required = false) String noteType,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder) {
        startPage();
        var query = readingNoteService.lambdaQuery()
                .eq(ReadingNote::getUserId, getUserId())
                .eq(documentId != null, ReadingNote::getDocumentId, documentId)
                .eq(noteType != null && !noteType.isEmpty(), ReadingNote::getNoteType, noteType);

        if ("asc".equalsIgnoreCase(sortOrder)) {
            query.orderByAsc(ReadingNote::getCreateTime);
        } else {
            query.orderByDesc(ReadingNote::getCreateTime);
        }

        List<ReadingNote> list = query.list();
        return getDataTable(list);
    }

    @GetMapping("/grouped-list")
    public AjaxResult groupedList(@RequestParam(required = false) String noteType,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder) {
        var query = readingNoteService.lambdaQuery()
                .eq(ReadingNote::getUserId, getUserId())
                .eq(noteType != null && !noteType.isEmpty(), ReadingNote::getNoteType, noteType);

        if ("asc".equalsIgnoreCase(sortOrder)) {
            query.orderByAsc(ReadingNote::getCreateTime);
        } else {
            query.orderByDesc(ReadingNote::getCreateTime);
        }
        List<ReadingNote> flatList = query.list();

        Map<LocalDate, List<ReadingNote>> groupedByDate = flatList.stream()
                .filter(note -> note.getCreateTime() != null) // 防御空指针
                .collect(Collectors.groupingBy(
                        note -> note.getCreateTime().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<ReadingNote>> entry : groupedByDate.entrySet()) {
            LocalDate date = entry.getKey();
            String label;
            if (date.isEqual(today)) {
                label = "今天";
            } else if (date.isEqual(yesterday)) {
                label = "昨天";
            } else {
                label = date.format(formatter);
            }

            Map<String, Object> group = new LinkedHashMap<>();
            group.put("date", date.toString());
            group.put("label", label);
            group.put("notes", entry.getValue());
            result.add(group);
        }

        return success(result);
    }

    @PostMapping
    public AjaxResult add(@RequestBody ReadingNote note) {
        note.setUserId(getUserId());
        note.setId(null); // 强制将ID设为null，确保执行INSERT而非UPDATE，防止前端误传id导致覆盖
        note.setContent(AiTextCleaner.clean(note.getContent()));
        note.setKnowledgePoints(AiTextCleaner.clean(note.getKnowledgePoints()));
        note.setCreateTime(java.time.LocalDateTime.now());
        note.setUpdateTime(java.time.LocalDateTime.now());
        readingNoteService.save(note);
        return success(note);
    }

    @PutMapping
    public AjaxResult edit(@RequestBody ReadingNote note) {
        if (note.getId() == null) {
            return error("更新操作必须提供笔记ID");
        }

        // 从数据库加载原始笔记，避免前端误传 null 覆盖自动填充字段
        ReadingNote original = readingNoteService.getById(note.getId());
        if (original == null || !original.getUserId().equals(getUserId())) {
            return error("笔记不存在或无权操作");
        }

        // 只更新前端明确传入的字段，避免编辑正文时把 documentId/sourceFile/noteType 等关联信息清空
        if (note.getDocumentId() != null) {
            original.setDocumentId(note.getDocumentId());
        }
        if (note.getParagraphIndex() != null) {
            original.setParagraphIndex(note.getParagraphIndex());
        }
        if (note.getSentence() != null) {
            original.setSentence(note.getSentence());
        }
        if (note.getContent() != null) {
            original.setContent(AiTextCleaner.clean(note.getContent()));
        }
        if (note.getSourceFile() != null) {
            original.setSourceFile(note.getSourceFile());
        }
        if (note.getNoteType() != null) {
            original.setNoteType(note.getNoteType());
        }
        if (note.getHighlightColor() != null) {
            original.setHighlightColor(note.getHighlightColor());
        }
        if (note.getKnowledgePoints() != null) {
            original.setKnowledgePoints(AiTextCleaner.clean(note.getKnowledgePoints()));
        }
        if (note.getSelectionText() != null) {
            original.setSelectionText(note.getSelectionText());
        }
        if (note.getSelectionStart() != null) {
            original.setSelectionStart(note.getSelectionStart());
        }
        if (note.getSelectionEnd() != null) {
            original.setSelectionEnd(note.getSelectionEnd());
        }

        return toAjax(readingNoteService.updateById(original));
    }

    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        return toAjax(readingNoteService.removeById(id));
    }

    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(readingNoteService.getById(id));
    }

    @PostMapping("/upload-image")
    public AjaxResult uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return error("请选择图片文件");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return error("仅支持图片文件");
        }

        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            return error("图片大小不能超过5MB");
        }

        String originalName = file.getOriginalFilename();
        String ext = "png";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase();
        }
        if (!List.of("jpg", "jpeg", "png", "gif", "webp", "bmp").contains(ext)) {
            return error("不支持的图片格式，仅支持 jpg/jpeg/png/gif/webp/bmp");
        }

        try {
            String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            String relativePath = "reading-notes/" + dateDir + "/" + fileName;

            Path dirPath = Paths.get(uploadBasePath, "reading-notes", dateDir);
            Files.createDirectories(dirPath);

            Path filePath = dirPath.resolve(fileName);
            file.transferTo(filePath.toFile());

            String url = uploadUrlPrefix + "/" + relativePath;

            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("url", url);
            result.put("fileName", fileName);
            result.put("originalName", originalName);
            result.put("size", file.getSize());
            result.put("contentType", contentType);
            result.put("relativePath", relativePath);

            return success(result);
        } catch (IOException e) {
            log.error("图片保存失败: {}", e.getMessage());
            return error("图片保存失败: " + e.getMessage());
        }
    }
}
