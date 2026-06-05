package com.geekyan.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.geekyan.entity.LongSentence;
import com.geekyan.service.IAiService;
import com.geekyan.service.IBaiduTranslateService;
import com.geekyan.service.ILongSentenceService;
import com.geekyan.service.IReviewTaskService;
import com.geekyan.util.AiTextCleaner;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/geekyan/long-sentence")
public class LongSentenceController extends BaseController {

    @Autowired
    private ILongSentenceService longSentenceService;

    @Autowired
    private IReviewTaskService reviewTaskService;

    @Autowired
    private IBaiduTranslateService baiduTranslateService;

    @Autowired
    private IAiService aiService;

    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String sentenceType) {
        startPage();
        List<LongSentence> list = longSentenceService.getUserSentences(getUserId(), difficulty, sentenceType);
        return getDataTable(list);
    }

    @PostMapping
    public AjaxResult add(@RequestBody LongSentence sentence) {
        if (sentence.getSentence() == null || sentence.getSentence().trim().isEmpty()) {
            return error("sentence字段不能为空");
        }
        LongSentence saved = longSentenceService.saveSentence(getUserId(), sentence);
        return success(saved);
    }

    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        return toAjax(longSentenceService.removeById(id));
    }

    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable Long id, @RequestBody LongSentence sentence) {
        LongSentence existing = longSentenceService.getById(id);
        if (existing == null || !existing.getUserId().equals(getUserId())) {
            return error("长难句不存在");
        }
        sentence.setId(id);
        sentence.setUserId(getUserId());
        sentence.setAnalysis(AiTextCleaner.clean(sentence.getAnalysis()));
        sentence.setTranslation(AiTextCleaner.clean(sentence.getTranslation()));
        sentence.setLiteralTranslation(AiTextCleaner.clean(sentence.getLiteralTranslation()));
        sentence.setFreeTranslation(AiTextCleaner.clean(sentence.getFreeTranslation()));
        sentence.setGrammarTags(AiTextCleaner.clean(sentence.getGrammarTags()));
        sentence.setCoreVocab(AiTextCleaner.clean(sentence.getCoreVocab()));
        return toAjax(longSentenceService.updateById(sentence));
    }

    @PostMapping("/{id}/add-review")
    public AjaxResult addToReview(@PathVariable Long id) {
        LongSentence sentence = longSentenceService.getById(id);
        if (sentence == null || !sentence.getUserId().equals(getUserId())) {
            return error("长难句不存在");
        }
        String content = sentence.getSentence();
        String answerContent = buildSentenceAnswerContent(sentence);
        reviewTaskService.createReviewTask(getUserId(), "sentence", id, content, answerContent, "english");
        return success("已加入复习计划");
    }

    private String buildSentenceAnswerContent(LongSentence sentence) {
        StringBuilder sb = new StringBuilder();
        if (sentence.getTranslation() != null && !sentence.getTranslation().isEmpty()) {
            sb.append("翻译：").append(sentence.getTranslation());
        }
        if (sentence.getGrammarTags() != null && !sentence.getGrammarTags().isEmpty()) {
            if (sb.length() > 0)
                sb.append("\n");
            sb.append("语法标签：").append(sentence.getGrammarTags());
        }
        if (sentence.getCoreVocab() != null && !sentence.getCoreVocab().isEmpty()) {
            if (sb.length() > 0)
                sb.append("\n");
            sb.append("核心词汇：").append(sentence.getCoreVocab());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    @PostMapping("/{id}/similar")
    public AjaxResult generateSimilar(@PathVariable Long id) {
        LongSentence sentence = longSentenceService.getById(id);
        if (sentence == null || !sentence.getUserId().equals(getUserId())) {
            return error("长难句不存在");
        }
        try {
            Map<String, Object> similar = longSentenceService.generateSimilarSentences(sentence);
            return success(similar);
        } catch (Exception e) {
            return error("生成同类语法句子失败: " + e.getMessage());
        }
    }

    @PostMapping("/generate-paragraph")
    public AjaxResult generateParagraph(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) params.get("sentenceIds");
        if (ids == null || ids.size() < 2) {
            return error("至少需要2个长难句才能生成段落");
        }
        List<Long> sentenceIds = new java.util.ArrayList<>();
        for (Integer id : ids) {
            sentenceIds.add(id.longValue());
        }
        try {
            Map<String, Object> result = longSentenceService.generateParagraphFromSentences(getUserId(), sentenceIds);
            if (result.containsKey("error")) {
                return error((String) result.get("error"));
            }
            return success(result);
        } catch (Exception e) {
            return error("AI生成段落失败: " + e.getMessage());
        }
    }

    @PostMapping("/create-from-chinese")
    public AjaxResult createFromChinese(@RequestBody Map<String, String> request) {
        String chineseSentence = request.get("chineseSentence");
        if (chineseSentence == null || chineseSentence.trim().isEmpty()) {
            return error("中文句子不能为空");
        }
        try {
            // 1. Translate Chinese to English
            String englishSentence = baiduTranslateService.translate(chineseSentence, "zh", "en");
            if (englishSentence == null || englishSentence.trim().isEmpty()) {
                return error("翻译失败，请稍后再试");
            }

            // 2. AI深度解析（四段式+结构化JSON）
            Map<String, Object> result = new HashMap<>();
            result.put("sentence", englishSentence);
            result.put("translation", chineseSentence);

            try {
                String analysis = aiService.chatWithoutHistory(
                        "你是考研英语辅导专家，擅长翻译和长难句教学。请对给定的句子进行翻译和深度解析。\n" +
                                "输出要求：\n" +
                                "1. 先输出四段式解析（用于前端展示）：\n" +
                                "【第1段：原句重现与整体感知】简要概括句子的核心意思和语言特点\n" +
                                "【第2段：核心词汇与地道表达】列出3-5个关键词汇，给出音标、词性、语境释义\n" +
                                "【第3段：语法名词剖析】用层级缩进展示句子语法结构，标注考研考点\n" +
                                "【第4段：深度解析与考点提示】翻译技巧、易错点、同类句型\n" +
                                "2. 在最后用```json包裹一段结构化数据：\n" +
                                "```json\n" +
                                "{\"sentence\":\"英文原句\",\"mainStructure\":\"主干提取\"," +
                                "\"grammarTags\":[\"语法标签1\",\"语法标签2\"]," +
                                "\"coreVocab\":[{\"word\":\"单词\",\"phonetic\":\"音标\",\"pos\":\"词性\",\"meaning\":\"语境含义\",\"isKey\":true}],"
                                +
                                "\"difficulty\":\"easy/medium/hard\"," +
                                "\"grammarPoints\":[\"语法知识点说明\"]," +
                                "\"examRelevance\":[\"考研考点关联\"]}\n" +
                                "```",
                        "原文：" + englishSentence + "\n中文：" + chineseSentence);

                if (analysis != null && !analysis.isEmpty()) {
                    // 解析四段式文本
                    String[] sectionLabels = { "第1段", "第2段", "第3段", "第4段" };
                    for (int si = 0; si < sectionLabels.length; si++) {
                        String marker = "【" + sectionLabels[si] + "：";
                        int start = analysis.indexOf(marker);
                        if (start != -1) {
                            start = analysis.indexOf("】", start) + 1;
                            int end = analysis.length();
                            for (int sj = si + 1; sj < sectionLabels.length; sj++) {
                                String nextMarker = "【" + sectionLabels[sj] + "：";
                                int nextPos = analysis.indexOf(nextMarker, start);
                                if (nextPos != -1 && nextPos < end) {
                                    end = nextPos;
                                }
                            }
                            int jsonBlockStart = analysis.lastIndexOf("```json");
                            if (jsonBlockStart != -1 && jsonBlockStart > start && jsonBlockStart < end) {
                                end = jsonBlockStart;
                            }
                            String sectionContent = analysis.substring(start, end).trim();
                            result.put("section" + (si + 1), sectionContent);
                        }
                    }

                    // 解析结构化JSON
                    int jsonStart = analysis.lastIndexOf("```json");
                    if (jsonStart != -1) {
                        int jsonContentStart = analysis.indexOf("\n", jsonStart) + 1;
                        int jsonEnd = analysis.indexOf("```", jsonContentStart);
                        if (jsonEnd != -1) {
                            String jsonStr = analysis.substring(jsonContentStart, jsonEnd).trim();
                            try {
                                JSONObject structured = JSON.parseObject(jsonStr);
                                if (structured != null) {
                                    result.put("structured", structured);
                                    // 兼容旧字段
                                    JSONObject analysisObj = new JSONObject();
                                    analysisObj.put("grammar_tags", structured.getJSONArray("grammarTags"));
                                    analysisObj.put("main_structure", structured.getString("mainStructure"));
                                    analysisObj.put("core_vocab", structured.getJSONArray("coreVocab"));
                                    analysisObj.put("difficulty", structured.getString("difficulty"));
                                    analysisObj.put("grammar_points", structured.getJSONArray("grammarPoints"));
                                    analysisObj.put("usage_notes", structured.getJSONArray("examRelevance"));
                                    result.put("analysis", analysisObj);
                                }
                            } catch (Exception parseEx) {
                                logger.warn("解析结构化JSON失败: {}", parseEx.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("AI深度解析失败: {}", e.getMessage());
            }

            // 3. Create LongSentence object and save
            LongSentence sentence = new LongSentence();
            sentence.setSentence(englishSentence);
            sentence.setTranslation(chineseSentence);
            sentence.setSource("中文句子翻译");
            sentence.setLiteralTranslation(null);
            sentence.setFreeTranslation(null);

            // 从structured中提取字段存入数据库
            Object structuredObj = result.get("structured");
            if (structuredObj instanceof JSONObject) {
                JSONObject sObj = (JSONObject) structuredObj;
                sentence.setGrammarTags(sObj.getJSONArray("grammarTags") != null
                        ? sObj.getJSONArray("grammarTags").toJSONString()
                        : null);
                sentence.setCoreVocab(sObj.getJSONArray("coreVocab") != null
                        ? sObj.getJSONArray("coreVocab").toJSONString()
                        : null);
                sentence.setDifficulty(sObj.getString("difficulty"));
                StringBuilder analysisText = new StringBuilder();
                String mainStructure = sObj.getString("mainStructure");
                if (mainStructure != null && !mainStructure.isEmpty()) {
                    analysisText.append("【句子主干】").append(mainStructure).append("\n\n");
                }
                for (int si = 1; si <= 4; si++) {
                    Object sec = result.get("section" + si);
                    if (sec != null) {
                        analysisText.append(sec.toString()).append("\n\n");
                    }
                }
                if (analysisText.length() > 0) {
                    sentence.setAnalysis(analysisText.toString().trim());
                }
            }

            LongSentence savedSentence = longSentenceService.saveSentence(getUserId(), sentence);
            result.put("id", savedSentence.getId());
            result.put("autoSaved", true);

            return success(result);
        } catch (Exception e) {
            logger.error("从中文创建长难句失败", e);
            return error("处理失败：" + e.getMessage());
        }
    }
}
