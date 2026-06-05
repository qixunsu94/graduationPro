package com.geekyan.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.LongSentence;
import com.geekyan.mapper.LongSentenceMapper;
import com.geekyan.service.ILongSentenceService;
import com.geekyan.service.IAiService;
import com.geekyan.service.IReviewTaskService;
import com.geekyan.util.AiTextCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LongSentenceServiceImpl extends ServiceImpl<LongSentenceMapper, LongSentence>
        implements ILongSentenceService {

    private static final Logger log = LoggerFactory.getLogger(LongSentenceServiceImpl.class);

    @Autowired
    private IAiService aiService;

    @Autowired
    private IReviewTaskService reviewTaskService;

    @Override
    public List<LongSentence> getUserSentences(Long userId, String difficulty, String sentenceType) {
        LambdaQueryWrapper<LongSentence> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LongSentence::getUserId, userId);
        if (difficulty != null && !difficulty.isEmpty()) {
            wrapper.eq(LongSentence::getDifficulty, difficulty);
        }
        if (sentenceType != null && !sentenceType.isEmpty()) {
            wrapper.eq(LongSentence::getSentenceType, sentenceType);
        }
        wrapper.orderByDesc(LongSentence::getCreateTime);
        return list(wrapper);
    }

    @Override
    public LongSentence saveSentence(Long userId, LongSentence sentence) {
        if (sentence.getSentence() == null || sentence.getSentence().trim().isEmpty()) {
            throw new RuntimeException("sentence字段不能为空");
        }
        sentence.setUserId(userId);
        cleanSentenceText(sentence);

        // 去重：同一用户同一句子+同一类型不重复保存
        String trimmedSentence = sentence.getSentence().trim();
        LambdaQueryWrapper<LongSentence> dedupWrapper = new LambdaQueryWrapper<LongSentence>()
                .eq(LongSentence::getUserId, userId)
                .eq(LongSentence::getSentence, trimmedSentence);
        // 如果指定了sentenceType，则按类型去重（同一句子可以同时有analysis和collection）
        if (sentence.getSentenceType() != null && !sentence.getSentenceType().trim().isEmpty()) {
            dedupWrapper.eq(LongSentence::getSentenceType, sentence.getSentenceType().trim());
        }
        LongSentence existing = getOne(dedupWrapper);
        if (existing != null) {
            log.info("长难句已存在，跳过保存: {}", trimmedSentence.substring(0, Math.min(30, trimmedSentence.length())));
            // 如果已有记录没有分析但新请求带了分析，则更新
            if ((existing.getAnalysis() == null || existing.getAnalysis().isEmpty())
                    && sentence.getAnalysis() != null && !sentence.getAnalysis().isEmpty()) {
                existing.setAnalysis(AiTextCleaner.clean(sentence.getAnalysis()));
                existing.setTranslation(AiTextCleaner.clean(sentence.getTranslation()));
                existing.setDifficulty(sentence.getDifficulty());
                updateById(existing);
                return existing;
            }
            return existing;
        }

        sentence.setSentence(trimmedSentence);

        // 验证sentenceType字段，确保只有合法值
        if (sentence.getSentenceType() == null || sentence.getSentenceType().trim().isEmpty()) {
            sentence.setSentenceType(null);
        } else {
            String type = sentence.getSentenceType().trim();
            if (!"analysis".equals(type) && !"collection".equals(type)) {
                sentence.setSentenceType(null);
            }
        }

        if (sentence.getAnalysis() == null || sentence.getAnalysis().trim().isEmpty()) {
            try {
                String analysis = aiService.analyzeGrammar(sentence.getSentence());
                if (analysis != null && !analysis.isEmpty() && !analysis.contains("unavailable")) {
                    sentence.setAnalysis(AiTextCleaner.clean(analysis));
                }
            } catch (Exception e) {
                log.warn("长难句AI语法分析失败: {}", e.getMessage());
            }
        }

        if (sentence.getTranslation() == null || sentence.getTranslation().trim().isEmpty()) {
            try {
                String translation = aiService.translate(sentence.getSentence(), "en", "zh");
                if (translation != null && !translation.isEmpty() && !translation.contains("unavailable")) {
                    sentence.setTranslation(AiTextCleaner.clean(translation));
                }
            } catch (Exception e) {
                log.warn("长难句AI翻译失败: {}", e.getMessage());
            }
        }

        save(sentence);

        try {
            reviewTaskService.createReviewTask(userId, "sentence", sentence.getId(),
                    sentence.getSentence(), "english");
        } catch (Exception e) {
            log.warn("创建长难句复习任务失败: {}", e.getMessage());
        }

        return sentence;
    }

    private void cleanSentenceText(LongSentence sentence) {
        sentence.setAnalysis(AiTextCleaner.clean(sentence.getAnalysis()));
        sentence.setTranslation(AiTextCleaner.clean(sentence.getTranslation()));
        sentence.setLiteralTranslation(AiTextCleaner.clean(sentence.getLiteralTranslation()));
        sentence.setFreeTranslation(AiTextCleaner.clean(sentence.getFreeTranslation()));
        sentence.setGrammarTags(AiTextCleaner.clean(sentence.getGrammarTags()));
        sentence.setCoreVocab(AiTextCleaner.clean(sentence.getCoreVocab()));
    }

    @Override
    public Map<String, Object> generateSimilarSentences(LongSentence sentence) {
        Map<String, Object> result = new HashMap<>();
        result.put("originalSentence", sentence.getSentence());

        String prompt = "你是英语语法教学专家。请根据以下长难句的语法结构，生成2-3句结构相似但内容不同的新句子。\n" +
                "严格返回JSON格式（不要markdown代码块）：\n" +
                "{\"similar_sentences\":[{\"sentence\":\"英文句子\",\"translation\":\"中文翻译\",\"structure_note\":\"结构说明\"}]}\n\n"
                +
                "原句：" + sentence.getSentence() + "\n";

        if (sentence.getAnalysis() != null && !sentence.getAnalysis().isEmpty()) {
            prompt += "语法分析：" + sentence.getAnalysis() + "\n";
        }

        prompt += "\n要求：\n1. 新句子必须与原句使用相同的核心语法结构\n2. 词汇和主题要不同\n3. 难度相当\n4. 只返回JSON";

        try {
            String aiResult = aiService.chat("你是英语语法教学专家。", prompt, "similar-sentence-" + sentence.getId());
            if (aiResult != null && !aiResult.isEmpty()) {
                String cleaned = aiResult.trim();
                if (cleaned.startsWith("```")) {
                    cleaned = cleaned.replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "").replaceAll("\\s*```$",
                            "");
                }
                try {
                    JSONObject parsed = JSON.parseObject(cleaned);
                    if (parsed != null && parsed.containsKey("similar_sentences")) {
                        JSONArray arr = parsed.getJSONArray("similar_sentences");
                        List<Map<String, String>> similarList = new ArrayList<>();
                        for (int i = 0; i < arr.size(); i++) {
                            JSONObject item = arr.getJSONObject(i);
                            Map<String, String> entry = new LinkedHashMap<>();
                            entry.put("sentence", item.getString("sentence"));
                            entry.put("translation", item.getString("translation"));
                            entry.put("structure_note", item.getString("structure_note"));
                            similarList.add(entry);
                        }
                        result.put("similarSentences", similarList);
                        return result;
                    }
                } catch (Exception e) {
                    log.warn("解析AI同类语法结果失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("AI同类语法生成失败: {}", e.getMessage());
        }

        result.put("similarSentences", new ArrayList<>());
        return result;
    }

    private static final String PARAGRAPH_SYSTEM_PROMPT =
            "你是一位资深的英语学习内容创作者，专门帮助考研学生把收藏的长难句转化为高质量的学习材料。你擅长：\n" +
            "- 从句子中提取语法结构、地道表达和写作技巧\n" +
            "- 判断一个句子被收藏的主要原因（语法经典 / 词汇亮点 / 句式优美）\n" +
            "- 将这些亮点自然地融入一个自创的英语段落中\n\n" +
            "你的创作原则：\n" +
            "- 生成的段落必须原创，不得直接复制任何原句\n" +
            "- 段落的语法结构和词汇难度应与考研英语真题相当\n" +
            "- 如果原句中有特别精彩的语法点，要在段落中刻意复现类似结构\n" +
            "- 如果原句中有高级词汇，要在段落中复用这些词汇\n" +
            "- 段落长度控制在 120-200 词\n" +
            "- 段落主题要统一，不能是句子的生硬拼接\n\n" +
            "你必须返回以下 JSON 结构，不要输出其他内容：\n" +
            "{\n" +
            "  \"analysis\": {\n" +
            "    \"main_focus\": \"本批次句子的主要学习方向（语法/词汇/句式/综合）\",\n" +
            "    \"key_points\": [{\"type\": \"grammar/expression/style\", \"content\": \"具体知识点\", \"from_sentence_index\": 1}],\n" +
            "    \"collection_reasons\": [{\"sentence_index\": 1, \"reason\": \"收藏原因\"}]\n" +
            "  },\n" +
            "  \"generated_paragraph\": \"生成的英语段落\",\n" +
            "  \"paragraph_translation\": \"段落的中文翻译\",\n" +
            "  \"learning_notes\": \"段落中运用的知识点说明（2-3句话）\"\n" +
            "}";

    @Override
    public Map<String, Object> generateParagraphFromSentences(Long userId, List<Long> sentenceIds) {
        Map<String, Object> result = new HashMap<>();

        // 1. 查询指定句子
        List<LongSentence> sentences = listByIds(sentenceIds);
        if (sentences == null || sentences.isEmpty()) {
            result.put("error", "未找到指定句子");
            return result;
        }
        // 过滤只保留当前用户的句子
        sentences.removeIf(s -> !s.getUserId().equals(userId));
        if (sentences.size() < 2) {
            result.put("error", "至少需要2个长难句才能生成段落");
            return result;
        }
        // 限制最多5个句子
        if (sentences.size() > 5) {
            sentences = sentences.subList(0, 5);
        }

        // 2. 构建 JSON 数组
        JSONArray sentenceArray = new JSONArray();
        for (int i = 0; i < sentences.size(); i++) {
            LongSentence s = sentences.get(i);
            JSONObject obj = new JSONObject();
            obj.put("index", i + 1);
            obj.put("sentence", s.getSentence());
            // source: "analysis" -> "ai_qa", "collection" -> "reading_collect"
            String src = "ai_qa";
            if ("collection".equals(s.getSentenceType())) {
                src = "reading_collect";
            }
            obj.put("source", src);
            // grammar_tags 和 vocab_highlights 从 analysis 中提取（如果有的话）
            obj.put("grammar_tags", extractGrammarTags(s.getAnalysis()));
            obj.put("vocab_highlights", extractVocabHighlights(s.getAnalysis()));
            sentenceArray.add(obj);
        }

        // 3. 组装 User Prompt
        String userPrompt = "请根据以下用户收藏的长难句，生成一个融合学习价值的英语段落。\n\n" +
                "## 用户收藏的句子\n" +
                sentenceArray.toJSONString() + "\n\n" +
                "每个句子包含以下信息：\n" +
                "- sentence: 英文原文\n" +
                "- source: 来源（ai_qa = AI答疑解析，reading_collect = 精读收藏）\n" +
                "- grammar_tags: 语法标签（可能为空）\n" +
                "- vocab_highlights: 核心词汇（可能为空）\n\n" +
                "## 你的任务\n\n" +
                "第一步：分析每个句子被收藏的可能原因\n" +
                "- 如果句子有明确的语法标签 → 重点学习该语法结构\n" +
                "- 如果句子有核心词汇 → 重点学习词汇用法\n" +
                "- 如果既没有语法标签也没有核心词汇 → 可能是句式优美或表达到位，学习其写作风格\n\n" +
                "第二步：从所有句子中提取 3-5 个最值得学习的知识点（可以是语法、词汇或句式）\n\n" +
                "第三步：创作一个原创英语段落，要求：\n" +
                "- 将提取的知识点自然地融入段落中\n" +
                "- 段落主题与考研阅读常考话题相关（科技/教育/社会/环境等）\n" +
                "- 如果有多个句子，尽量让段落的逻辑连贯，而非简单罗列\n" +
                "- 段落中必须包含至少 3 个来自原句的高级词汇或短语\n" +
                "- 段落中必须复现至少 1 个来自原句的语法结构（如倒装、虚拟语气、分词作状语等）";

        // 4. 调用 AI
        try {
            String sessionId = "sentence-paragraph-" + userId + "-" + System.currentTimeMillis();
            String aiResult = aiService.chat(PARAGRAPH_SYSTEM_PROMPT, userPrompt, sessionId);

            if (aiResult != null && !aiResult.isEmpty()) {
                String cleaned = aiResult.trim();
                // 去除 markdown 代码块包裹
                if (cleaned.startsWith("```")) {
                    cleaned = cleaned.replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "").replaceAll("\\s*```$", "");
                }
                try {
                    JSONObject parsed = JSON.parseObject(cleaned);
                    if (parsed != null) {
                        result.put("analysis", parsed.getJSONObject("analysis"));
                        result.put("generated_paragraph", parsed.getString("generated_paragraph"));
                        result.put("paragraph_translation", parsed.getString("paragraph_translation"));
                        result.put("learning_notes", parsed.getString("learning_notes"));
                        return result;
                    }
                } catch (Exception e) {
                    log.warn("解析AI生成段落JSON失败，返回原始文本: {}", e.getMessage());
                }
                // JSON 解析失败，返回原始文本
                result.put("generated_paragraph", aiResult);
                result.put("paragraph_translation", "");
                result.put("learning_notes", "");
                return result;
            }
        } catch (Exception e) {
            log.error("AI生成长难句段落失败: {}", e.getMessage());
        }

        result.put("error", "AI生成失败，请稍后重试");
        return result;
    }

    /**
     * 从解析文本中提取语法标签
     */
    private List<String> extractGrammarTags(String analysis) {
        List<String> tags = new ArrayList<>();
        if (analysis == null || analysis.isEmpty()) return tags;
        // 匹配第3段中的语法关键词
        String[] keywords = {"倒装", "虚拟语气", "强调句", "省略句", "非谓语", "分词", "定语从句",
                "宾语从句", "主语从句", "表语从句", "同位语从句", "状语从句", "非限制性",
                "插入语", "形式主语", "形式宾语", "并列句", "复合句", "被动语态"};
        for (String kw : keywords) {
            if (analysis.contains(kw)) {
                tags.add(kw);
            }
        }
        return tags;
    }

    /**
     * 从解析文本中提取核心词汇
     */
    private List<String> extractVocabHighlights(String analysis) {
        List<String> vocabs = new ArrayList<>();
        if (analysis == null || analysis.isEmpty()) return vocabs;
        // 匹配第2段中的词汇格式: word /phonetic/ (pos) - 含义
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([a-zA-Z\\s'-]+)\\s*/[^/]*/\\s*(?:\\([^)]*\\))?\\s*[—\\-–]").matcher(analysis);
        int count = 0;
        while (m.find() && count < 6) {
            String word = m.group(1).trim();
            if (word.length() > 2 && !word.equalsIgnoreCase("the") && !word.equalsIgnoreCase("and")) {
                vocabs.add(word);
                count++;
            }
        }
        return vocabs;
    }
}
