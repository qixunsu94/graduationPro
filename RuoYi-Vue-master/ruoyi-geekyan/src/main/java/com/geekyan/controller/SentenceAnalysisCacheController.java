package com.geekyan.controller;

import com.geekyan.entity.SentenceAnalysisCache;
import com.geekyan.service.ISentenceAnalysisCacheService;
import com.geekyan.service.IAiService;
import com.geekyan.service.QueryCacheService;
import com.geekyan.util.AiTextCleaner;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 精读句子解析缓存控制器
 * 提供缓存查询和AI解析接口，前端优先查缓存，缓存未命中再调AI
 */
@RestController
@RequestMapping("/geekyan/sentence-analysis")
public class SentenceAnalysisCacheController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(SentenceAnalysisCacheController.class);

    @Autowired
    private ISentenceAnalysisCacheService cacheService;

    @Autowired
    private IAiService aiService;

    @Autowired
    private QueryCacheService queryCacheService;

    /**
     * 查询缓存：根据句子内容查询该用户的解析缓存
     * GET /geekyan/sentence-analysis/cache?sentence=xxx
     */
    @GetMapping("/cache")
    public AjaxResult getCache(@RequestParam String sentence) {
        if (sentence == null || sentence.trim().isEmpty()) {
            return error("sentence参数不能为空");
        }

        // 1. 先查Redis缓存
        String redisKey = "sentence:" + sentence.trim();
        String redisCached = queryCacheService.getSentenceAnalysis(redisKey);
        if (redisCached != null && !redisCached.isEmpty()) {
            redisCached = AiTextCleaner.clean(redisCached);
            Map<String, Object> data = new HashMap<>();
            data.put("cached", true);
            data.put("source", "redis");
            data.put("fullAnalysis", redisCached);
            return success(data);
        }

        // 2. 再查数据库缓存
        SentenceAnalysisCache dbCache = cacheService.getByUserAndSentence(getUserId(), sentence.trim());
        if (dbCache != null && dbCache.getFullAnalysis() != null && !dbCache.getFullAnalysis().isEmpty()) {
            // 回写Redis
            queryCacheService.setSentenceAnalysis(redisKey, AiTextCleaner.clean(dbCache.getFullAnalysis()));
            // 更新命中次数
            cacheService.incrementHitCount(dbCache.getId());

            Map<String, Object> data = new HashMap<>();
            data.put("cached", true);
            data.put("source", "database");
            data.put("fullAnalysis", AiTextCleaner.clean(dbCache.getFullAnalysis()));
            data.put("section1", AiTextCleaner.clean(dbCache.getSection1()));
            data.put("section2", AiTextCleaner.clean(dbCache.getSection2()));
            data.put("section3", AiTextCleaner.clean(dbCache.getSection3()));
            data.put("section4", AiTextCleaner.clean(dbCache.getSection4()));
            data.put("translateLiteral", AiTextCleaner.clean(dbCache.getTranslateLiteral()));
            data.put("translateFree", AiTextCleaner.clean(dbCache.getTranslateFree()));
            return success(data);
        }

        // 3. 无缓存
        return success(new HashMap<String, Object>() {{
            put("cached", false);
        }});
    }

    /**
     * 保存解析缓存：AI解析完成后将结果存入缓存
     * POST /geekyan/sentence-analysis/cache
     */
    @PostMapping("/cache")
    public AjaxResult saveCache(@RequestBody Map<String, Object> params) {
        String sentence = (String) params.get("sentence");
        String fullAnalysis = (String) params.get("fullAnalysis");
        String section1 = (String) params.get("section1");
        String section2 = (String) params.get("section2");
        String section3 = (String) params.get("section3");
        String section4 = (String) params.get("section4");
        String translateLiteral = (String) params.get("translateLiteral");
        String translateFree = (String) params.get("translateFree");

        if (sentence == null || sentence.trim().isEmpty()) {
            return error("sentence不能为空");
        }
        if (fullAnalysis == null || fullAnalysis.trim().isEmpty()) {
            return error("fullAnalysis不能为空");
        }
        fullAnalysis = AiTextCleaner.clean(fullAnalysis);
        section1 = AiTextCleaner.clean(section1);
        section2 = AiTextCleaner.clean(section2);
        section3 = AiTextCleaner.clean(section3);
        section4 = AiTextCleaner.clean(section4);
        translateLiteral = AiTextCleaner.clean(translateLiteral);
        translateFree = AiTextCleaner.clean(translateFree);

        SentenceAnalysisCache cache = new SentenceAnalysisCache();
        cache.setUserId(getUserId());
        cache.setSentence(sentence.trim());
        cache.setFullAnalysis(fullAnalysis);
        cache.setSection1(section1);
        cache.setSection2(section2);
        cache.setSection3(section3);
        cache.setSection4(section4);
        cache.setTranslateLiteral(translateLiteral);
        cache.setTranslateFree(translateFree);
        cache.setAiModel("glm-4-flash");

        SentenceAnalysisCache saved = cacheService.saveCache(cache);

        // 同步写入Redis
        String redisKey = "sentence:" + sentence.trim();
        queryCacheService.setSentenceAnalysis(redisKey, fullAnalysis);

        return success(saved);
    }

    /**
     * 解析句子（带缓存）：优先返回缓存，未命中则调AI并缓存结果
     * POST /geekyan/sentence-analysis/analyze
     */
    @PostMapping("/analyze")
    public AjaxResult analyzeWithCache(@RequestBody Map<String, Object> params) {
        String sentence = (String) params.get("sentence");
        if (sentence == null || sentence.trim().isEmpty()) {
            return error("sentence不能为空");
        }

        String trimmed = sentence.trim();

        // 1. 查缓存
        SentenceAnalysisCache dbCache = cacheService.getByUserAndSentence(getUserId(), trimmed);
        if (dbCache != null && dbCache.getFullAnalysis() != null && !dbCache.getFullAnalysis().isEmpty()) {
            cacheService.incrementHitCount(dbCache.getId());
            Map<String, Object> data = new HashMap<>();
            data.put("cached", true);
            data.put("fullAnalysis", AiTextCleaner.clean(dbCache.getFullAnalysis()));
            data.put("section1", AiTextCleaner.clean(dbCache.getSection1()));
            data.put("section2", AiTextCleaner.clean(dbCache.getSection2()));
            data.put("section3", AiTextCleaner.clean(dbCache.getSection3()));
            data.put("section4", AiTextCleaner.clean(dbCache.getSection4()));
            data.put("translateLiteral", AiTextCleaner.clean(dbCache.getTranslateLiteral()));
            data.put("translateFree", AiTextCleaner.clean(dbCache.getTranslateFree()));
            return success(data);
        }

        // 2. 缓存未命中，调AI
        String prompt = buildAnalyzePrompt(trimmed);
        String aiResult = null;
        try {
            aiResult = aiService.chat("你是一位考研英语语法分析专家。", prompt, "sentence-analysis");
        } catch (Exception e) {
            log.error("AI句子解析失败: {}", e.getMessage());
            return error("AI解析服务暂时不可用，请稍后重试");
        }

        if (aiResult == null || aiResult.isEmpty() || aiResult.contains("unavailable")) {
            return error("AI解析服务暂时不可用，请稍后重试");
        }

        // 3. 解析4段内容
        String section1 = AiTextCleaner.clean(extractSection(aiResult, 1));
        String section2 = AiTextCleaner.clean(extractSection(aiResult, 2));
        String section3 = AiTextCleaner.clean(extractSection(aiResult, 3));
        String section4 = AiTextCleaner.clean(extractSection(aiResult, 4));
        String translateLiteral = extractTranslation(section4, "直译");
        String translateFree = extractTranslation(section4, "意译");
        String cleanedAiResult = AiTextCleaner.clean(aiResult);

        // 4. 存入缓存
        SentenceAnalysisCache cache = new SentenceAnalysisCache();
        cache.setUserId(getUserId());
        cache.setSentence(trimmed);
        cache.setFullAnalysis(cleanedAiResult);
        cache.setSection1(section1);
        cache.setSection2(section2);
        cache.setSection3(section3);
        cache.setSection4(section4);
        cache.setTranslateLiteral(translateLiteral);
        cache.setTranslateFree(translateFree);
        cache.setAiModel("glm-4-flash");
        cacheService.saveCache(cache);

        // 5. 返回结果
        Map<String, Object> data = new HashMap<>();
        data.put("cached", false);
        data.put("fullAnalysis", cleanedAiResult);
        data.put("section1", section1);
        data.put("section2", section2);
        data.put("section3", section3);
        data.put("section4", section4);
        data.put("translateLiteral", translateLiteral);
        data.put("translateFree", translateFree);
        return success(data);
    }

    private String buildAnalyzePrompt(String sentence) {
        return "请对以下英语句子进行深度解析，严格按照4段式输出协议，每段用【】标记开头：\n\n"
                + "原句：" + sentence + "\n\n"
                + "【第1段：原句重现与整体感知】\n"
                + "展示原句，然后用一句话概括句子的核心意思和表达功能。\n\n"
                + "【第2段：核心词汇与地道表达】\n"
                + "从句子中提取3-8个核心词汇或地道表达。每个词汇展示：拼写、音标、在当前句中的词性、在当前句中的具体含义。格式：word /phonetic/ (pos) - 句中含义\n\n"
                + "【第3段：语法名词剖析】\n"
                + "以缩进层级结构展示句子的语法成分，标注主谓宾、从句类型、非谓语动词、特殊句式、时态与语态、修饰关系。\n\n"
                + "【第4段：深度解析与考点提示】\n"
                + "包含句子结构特点、翻译技巧（直译和意译各给出一种）、考研考点关联、理解难点突破。";
    }

    private String extractSection(String text, int sectionNum) {
        String pattern = "【第" + sectionNum + "段[：:][^】]*】([\\s\\S]*?)(?=【第" + (sectionNum + 1) + "段|$)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(text);
        if (m.find()) return m.group(1).trim();
        return "";
    }

    private String extractTranslation(String section4, String type) {
        String pattern = type + "[：:]\\s*(.+)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(section4);
        if (m.find()) return m.group(1).trim();
        return "";
    }
}
