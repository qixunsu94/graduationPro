package com.geekyan.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.geekyan.entity.TranslationCache;
import com.geekyan.entity.Word;
import com.geekyan.service.IBaiduTranslateService;
import com.geekyan.service.IAiService;
import com.geekyan.service.IDictionaryService;
import com.geekyan.service.IOfflineDictService;
import com.geekyan.service.ITranslationCacheService;
import com.geekyan.service.IWordService;
import com.geekyan.service.ILongSentenceService;
import com.geekyan.service.QueryCacheService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/geekyan/translate")
public class TranslateController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(TranslateController.class);

    @Autowired
    private IBaiduTranslateService baiduTranslateService;

    @Autowired
    private IAiService aiService;

    @Autowired
    private ITranslationCacheService translationCacheService;

    @Autowired
    private QueryCacheService queryCacheService;

    @Autowired
    private IWordService wordService;

    @Autowired
    private IDictionaryService dictionaryService;

    @Autowired
    private IOfflineDictService offlineDictService;

    @Autowired
    private ILongSentenceService longSentenceService;

    // 判断纯英文（允许空格和标点）
    private static final Pattern ENGLISH_PATTERN = Pattern.compile("^[a-zA-Z\\s.,?!;:'\"\\-]+$");
    // 判断单个英文单词（不含空格和标点）
    private static final Pattern SINGLE_ENGLISH_WORD_PATTERN = Pattern.compile("^[a-zA-Z'\\-]+$");
    private static final Pattern ENGLISH_TOKEN_PATTERN = Pattern.compile("[A-Za-z]+(?:[-'][A-Za-z]+)*");
    private static final Pattern ENGLISH_SENTENCE_PUNCT_PATTERN = Pattern.compile("[.?!;:]");
    private static final Pattern ENGLISH_SUBORDINATE_PATTERN = Pattern.compile(
            "\\b(that|which|who|whom|whose|where|when|why|because|if|whether|although|though|while|since|unless|until|before|after)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH_PREDICATE_SIGNAL_PATTERN = Pattern.compile(
            "\\b(am|is|are|was|were|be|been|being|do|does|did|have|has|had|can|could|will|would|shall|should|may|might|must)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH_QUESTION_STRUCTURE_PATTERN = Pattern.compile(
            "\\b(what|where|when|why|how|which|who|whom|whose)\\b.+\\b(am|is|are|was|were|do|does|did|can|could|will|would|should|may|might|must|have|has|had)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH_COMMON_VERB_PATTERN = Pattern.compile(
            "\\b(go|goes|come|comes|make|makes|take|takes|get|gets|give|gives|put|puts|look|looks|see|sees|hear|hears|know|knows|think|thinks|want|wants|need|needs|mean|means|become|becomes|seem|seems|feel|feels|use|uses|show|shows|tell|tells|ask|asks|try|tries|work|works|study|studies|learn|learns|read|reads|write|writes|blow|blows|run|runs|move|moves|change|changes|happen|happens|depend|depends|apply|applies|applied)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH_PARTICIPLE_COMPLEMENT_PATTERN = Pattern.compile(
            "\\b(applied|used|based|known|related|connected|linked|designed|made|built|written|called|given|taken|shown|found)\\b\\s+\\b(to|for|in|on|with|by|from|as)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH_PREPOSITION_START_PATTERN = Pattern.compile(
            "^(in|on|at|to|for|from|with|without|of|by|as|about|under|over|between|among|through|during|before|after)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * 统一翻译查询接口 - 智能判断用户意图，支持六种转换
     * 英文单词→中文、英文短语→中文、英文句子→中文、中文词→英文、中文短语→英文、中文句子→英文
     */
    @PostMapping("/query")
    public AjaxResult query(@RequestBody Map<String, String> params) {
        String q = params.get("q");
        if (q == null || q.trim().isEmpty()) {
            return error("查询内容不能为空");
        }
        q = q.trim();

        log.info("收到统一查询请求: q='{}'", q);

        // 0. 统一缓存检查：所有查询类型都走缓存
        try {
            String cachedJson = queryCacheService.getUnifiedQuery(q);
            if (cachedJson != null && !cachedJson.isEmpty()) {
                try {
                    Map<String, Object> cachedMap = JSON.parseObject(cachedJson,
                            new com.alibaba.fastjson2.TypeReference<Map<String, Object>>() {
                            });
                    if (cachedMap != null && cachedMap.containsKey("queryType")) {
                        cachedMap.put("cached", true);
                        log.info("统一查询缓存命中: q='{}', type={}", q, cachedMap.get("queryType"));
                        return success(cachedMap);
                    }
                } catch (Exception e) {
                    log.warn("统一查询缓存解析失败，重新查询: q='{}'", q);
                }
            }
        } catch (Exception e) {
            log.warn("统一查询缓存读取失败: q='{}', error={}", q, e.getMessage());
        }

        // 1. 智能意图判断
        String queryType = detectQueryType(q);
        log.info("判断查询类型为: {}", queryType);

        // 2. 查询路由分发
        Map<String, Object> response = new HashMap<>();
        response.put("query", q);
        response.put("queryType", queryType);

        switch (queryType) {
            case "EN_WORD_TO_ZH":
                handleEnWordToZh(q, response);
                break;
            case "EN_PHRASE_TO_ZH":
                handleEnPhraseToZh(q, response);
                break;
            case "EN_SENTENCE_TO_ZH":
                handleEnSentenceToZh(q, response);
                break;
            case "ZH_WORD_TO_EN":
                handleZhWordToEn(q, response);
                break;
            case "ZH_PHRASE_TO_EN":
                handleZhPhraseToEn(q, response);
                break;
            case "ZH_SENTENCE_TO_EN":
                handleZhSentenceToEn(q, response);
                break;
            default:
                return error("未知的查询类型");
        }

        // 3. 生成历史记录摘要
        response.put("historyEntry", createHistoryEntry(response));

        // 4. 保存统一查询缓存
        try {
            queryCacheService.setUnifiedQuery(q, response);
        } catch (Exception e) {
            log.warn("统一查询缓存写入失败: q='{}', error={}", q, e.getMessage());
        }

        return success(response);
    }

    /**
     * 智能判断查询类型
     * 六种类型：EN_WORD_TO_ZH / EN_PHRASE_TO_ZH / EN_SENTENCE_TO_ZH
     * ZH_WORD_TO_EN / ZH_PHRASE_TO_EN / ZH_SENTENCE_TO_EN
     */
    private String detectQueryType(String q) {
        boolean isEnglish = ENGLISH_PATTERN.matcher(q).matches();
        if (isEnglish) {
            return detectEnglishQueryType(q);
        }

        // 非英文输入视为中文
        long chineseCharCount = q.chars().filter(c -> c >= 0x4e00 && c <= 0x9fa5).count();

        // === 多维度句子信号检测 ===
        // 1. 标点信号（最强）：句子级标点几乎100%表明是句子
        boolean hasSentencePunct = q.matches(".*[。？！；…].*");
        boolean hasClausePunct = q.matches(".*[，、：].*"); // 分句标点

        // 2. 句式信号：疑问句式、否定反问句式
        boolean hasQuestionPattern = q.matches(".*[吗呢吧啊呀嘛呗].*") // 语气词结尾
                || q.matches(".*(是不是|有没有|能不能|会不会|要不要|该不该|懂不懂).*") // 正反问句
                || q.matches(".*(谁|什么|哪|怎么|怎样|为什么|为何|是否|几|多少|何|孰).*"); // 疑问代词

        // 3. 逻辑连接词信号：出现关联词说明是复句
        boolean hasConjunction = q
                .matches(".*(但是|可是|不过|然而|因为|所以|虽然|尽管|如果|假如|只要|只有|除非|而且|并且|然后|于是|因此|否则|或者|还是|不仅|不但|既然|即使|就算).*");

        // 4. 主谓结构信号：代词+谓语
        boolean hasSubjectPredicate = q
                .matches(".*(我|你|他|她|它|我们|你们|他们|这|那|大家|自己|别人).*(是|有|在|会|能|要|想|去|来|做|说|看|听|知道|觉得|认为|希望|喜欢|需要|应该|可以).*");

        // 5. 口语化谓语信号：含"都要+动作""得+补语""了+宾语"等典型谓语结构
        boolean hasColloquialPredicate = q.matches(".*(都要|都要了|都得|还得|就要|快要|正在|一直在|不停地|不断地).*")
                || q.matches(".*(得|不)了.*") // "受不了""来得及"
                || q.matches(".*(打|转|转转|晕|累|忙|急|慌|疯|傻|懵|醉|醒).*") // 口语状态动词
                        && q.matches(".*(都|要|快|要了|死了|坏了|极了|得不得了).*"); // +程度补语

        // 6. 谓语动词+宾语/补语信号：常见动词后接名词或方向补语
        // 精确匹配：动词+了/着/过/到/得/在 + 后续成分
        boolean hasVerbPhrase = q
                .matches(".*(是|有|在|会|能|要|想|去|来|做|说|看|听|吃|喝|走|跑|飞|吹|打|拿|放|写|读|学|用|给|带|叫|让|把|被|比|跟)(了|着|过|到|得|在).*")
                || q.matches(".*(往|向|从|到).*(去|来|走|跑|飞|吹|打|拿|放|看|听|说|吃).*") // "往哪边吹""从哪里来"
                || q.matches(".*(按|照|靠|借|对|为|给|让|把|被).*(做|办|说|看|写|排|对|齐).*"); // "按模板排""对备注压缩"

        // 7. 名词短语排除信号：纯名词组合，无谓语
        boolean isNounPhrase = q.matches("^[^，。？！；…的了着过吗呢吧啊呀呗]+$") // 无标点无语气词无助词
                && !hasVerbPhrase // 无动宾/动补结构
                && !hasColloquialPredicate // 无口语谓语
                && !hasSubjectPredicate // 无主谓结构
                && !hasQuestionPattern; // 无疑问词

        // 综合评分：每个信号加权
        int sentenceScore = 0;
        if (hasSentencePunct)
            sentenceScore += 3; // 最强信号
        if (hasClausePunct)
            sentenceScore += 2;
        if (hasQuestionPattern)
            sentenceScore += 2;
        if (hasConjunction)
            sentenceScore += 2;
        if (hasSubjectPredicate)
            sentenceScore += 2;
        if (hasColloquialPredicate)
            sentenceScore += 2; // 口语化谓语
        if (hasVerbPhrase)
            sentenceScore += 1; // 动宾/动补结构
        // 长度信号
        if (chineseCharCount >= 5)
            sentenceScore += 1;
        if (chineseCharCount >= 8)
            sentenceScore += 1;
        if (chineseCharCount >= 12)
            sentenceScore += 1;

        // 名词短语排除：如果明确是名词短语，减分
        if (isNounPhrase && chineseCharCount <= 6) {
            sentenceScore -= 2;
        }

        // 学科术语后缀检测：含"算法/原理/方法/定理/定律/效应/模型/策略/机制/理论"等的是专业名词，走查词
        boolean isAcademicTerm = q.matches(
                ".*(算法|原理|方法|定理|定律|效应|模型|策略|机制|理论|概念|定义|公式|法则|技术|工艺|流程|体系|系统|架构|框架|范式|标准|规范|协议|原则|准则|模式|方案|设计|结构|组织|制度|规则|程序|步骤|过程|阶段|周期|周期|要素|因素|条件|参数|指标|特征|属性|性质|功能|作用|意义|价值|目的|目标|任务|问题|现象|规律|趋势|趋势|状态|形态|形式|类型|分类|类别|等级|层次|层面|角度|维度|视角|观点|立场|态度|观点).*");
        if (isAcademicTerm && isNounPhrase) {
            sentenceScore -= 3; // 强烈倾向走查词
        }

        // 兜底规则：短语/句子冲突时优先句子
        // 评分 ≥ 2 → 句子；= 1 且字数 ≥ 4 → 句子；≤ 0 按字数分词/短语
        if (sentenceScore >= 2 || (sentenceScore >= 1 && chineseCharCount >= 4)) {
            return "ZH_SENTENCE_TO_EN";
        }
        if (chineseCharCount <= 2) {
            // 2字中文动词/动宾短语走翻译路线效果更好，纯名词走反向查词
            if (isTwoCharChineseVerb(q)) {
                return "ZH_PHRASE_TO_EN";
            }
            return "ZH_WORD_TO_EN";
        }
        // 学科术语/纯名词短语 → 走查词（反向搜索+AI分析），而非翻译
        if (isAcademicTerm || (isNounPhrase && sentenceScore <= -1)) {
            return "ZH_WORD_TO_EN";
        }
        // 3+字且无任何句子信号 → 短语
        return "ZH_PHRASE_TO_EN";
    }

    /**
     * 判断2字中文是否为动词/动宾短语，走翻译路线而非反向查词
     * 反向查词适合纯名词（苹果→apple），动词（拍摄→film/shoot）走翻译更精准
     */
    private static final Set<String> TWO_CHAR_VERBS = Set.of(
            // 认知/思维
            "学习", "理解", "记忆", "思考", "分析", "研究", "探索", "发现", "认识", "了解",
            "判断", "推理", "想象", "猜测", "预测", "估计", "计算", "统计", "测量",
            // 创造/表达
            "拍摄", "创作", "设计", "发明", "编写", "撰写", "书写", "记录", "表达", "描述",
            "阐述", "说明", "解释", "翻译", "绘画", "演奏", "表演",
            // 交流/社交
            "沟通", "交流", "合作", "协商", "讨论", "辩论", "争论", "谈判", "推荐", "介绍",
            "宣传", "传播", "通知", "告知", "提醒", "警告", "宣布", "声明",
            // 管理/组织
            "组织", "管理", "领导", "指挥", "控制", "调整", "安排", "部署", "分配", "协调",
            "监督", "检查", "审核", "评估", "规划", "策划",
            // 变化/发展
            "发展", "改变", "转变", "转化", "演变", "进化", "进步", "改善", "提升", "优化",
            "强化", "增强", "扩大", "缩小", "增加", "减少", "扩展", "拓展",
            // 动作/行为
            "训练", "练习", "实践", "执行", "实施", "操作", "使用", "运用", "应用", "采用",
            "选择", "挑选", "筛选", "整理", "清理", "删除", "添加", "修改", "修复", "维护",
            "保护", "支持", "帮助", "协助", "配合", "参与", "加入", "退出", "放弃", "坚持",
            "继续", "完成", "实现", "达成", "解决", "处理", "应对", "克服", "突破", "超越",
            // 教育/培养
            "教育", "培养", "指导", "辅导", "教导", "启发", "引导", "传授",
            // 生活/日常
            "生活", "工作", "运动", "旅行", "休息", "睡觉", "吃饭", "喝水", "走路", "跑步",
            "游泳", "骑车", "开车", "飞行", "航海",
            // 情感/态度
            "喜欢", "讨厌", "热爱", "珍惜", "尊重", "信任", "依赖", "相信", "感受", "体验",
            "经历", "承受", "忍受", "享受");

    private static boolean isTwoCharChineseVerb(String q) {
        return TWO_CHAR_VERBS.contains(q);
    }

    static String detectEnglishQueryType(String q) {
        String text = normalizeEnglishInput(q);
        List<String> tokens = extractEnglishTokens(text);
        int wordCount = tokens.size();
        if (wordCount <= 1 && SINGLE_ENGLISH_WORD_PATTERN.matcher(text).matches()) {
            return "EN_WORD_TO_ZH";
        }
        if (ENGLISH_SENTENCE_PUNCT_PATTERN.matcher(text).find()) {
            return "EN_SENTENCE_TO_ZH";
        }
        if (wordCount >= 7) {
            return "EN_SENTENCE_TO_ZH";
        }
        if (ENGLISH_SUBORDINATE_PATTERN.matcher(text).find()) {
            return "EN_SENTENCE_TO_ZH";
        }
        if (ENGLISH_PREDICATE_SIGNAL_PATTERN.matcher(text).find()) {
            return "EN_SENTENCE_TO_ZH";
        }
        if (ENGLISH_QUESTION_STRUCTURE_PATTERN.matcher(text).find()) {
            return "EN_SENTENCE_TO_ZH";
        }
        if (hasEnglishVerbObjectOrComplement(text, tokens)) {
            return "EN_SENTENCE_TO_ZH";
        }
        return "EN_PHRASE_TO_ZH";
    }

    private static String normalizeEnglishInput(String q) {
        if (q == null) {
            return "";
        }
        return q.trim()
                .replaceAll("^[\"'“”‘’]+|[\"'“”‘’]+$", "")
                .replaceAll("\\s+", " ");
    }

    private static List<String> extractEnglishTokens(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = ENGLISH_TOKEN_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            tokens.add(matcher.group().toLowerCase());
        }
        return tokens;
    }

    private static boolean hasEnglishVerbObjectOrComplement(String text, List<String> tokens) {
        if (tokens.size() < 3) {
            return false;
        }
        if (ENGLISH_PARTICIPLE_COMPLEMENT_PATTERN.matcher(text).find()) {
            return true;
        }
        String normalized = normalizeEnglishInput(text).toLowerCase();
        if (isKnownShortVerbPhrase(normalized)) {
            return false;
        }
        Matcher verbMatcher = ENGLISH_COMMON_VERB_PATTERN.matcher(normalized);
        if (!verbMatcher.find()) {
            return false;
        }
        String firstToken = tokens.get(0);
        boolean startsWithPreposition = ENGLISH_PREPOSITION_START_PATTERN.matcher(normalized).find();
        if (startsWithPreposition) {
            return false;
        }
        int verbIndex = tokens.indexOf(verbMatcher.group().toLowerCase());
        if (verbIndex > 0) {
            return true;
        }
        return !isLikelyTwoWordVerbPhrase(firstToken, tokens) && tokens.size() >= 3;
    }

    private static boolean isKnownShortVerbPhrase(String normalized) {
        return "take off".equals(normalized)
                || "look up".equals(normalized)
                || "give up".equals(normalized)
                || "put off".equals(normalized)
                || "turn on".equals(normalized)
                || "turn off".equals(normalized)
                || "set up".equals(normalized)
                || "come on".equals(normalized)
                || "go on".equals(normalized);
    }

    private static boolean isLikelyTwoWordVerbPhrase(String firstToken, List<String> tokens) {
        if (tokens.size() != 2) {
            return false;
        }
        String secondToken = tokens.get(1);
        return ("take".equals(firstToken) || "look".equals(firstToken) || "give".equals(firstToken)
                || "put".equals(firstToken) || "turn".equals(firstToken) || "set".equals(firstToken)
                || "come".equals(firstToken) || "go".equals(firstToken))
                && ("off".equals(secondToken) || "up".equals(secondToken) || "on".equals(secondToken));
    }

    /**
     * 英文单词 → 中文：查词 + 翻译
     */
    private void handleEnWordToZh(String q, Map<String, Object> response) {
        // 1. 查词逻辑（复用WordController的核心流程）
        Word wordDetail = null;
        try {
            wordDetail = wordService.searchWord(q.toLowerCase());
        } catch (Exception e) {
            log.warn("数据库查词失败: q='{}', error={}", q, e.getMessage());
        }

        if (wordDetail == null) {
            try {
                wordDetail = dictionaryService.lookupWord(q.toLowerCase());
                if (wordDetail != null && wordDetail.getMeanings() != null && !wordDetail.getMeanings().isEmpty()) {
                    try {
                        wordDetail.setCreateTime(LocalDateTime.now());
                        wordDetail.setUpdateTime(LocalDateTime.now());
                        wordService.save(wordDetail);
                    } catch (Exception e) {
                        log.warn("保存单词失败: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("第三方词典查词失败: q='{}', error={}", q, e.getMessage());
            }
        }

        if (wordDetail == null || wordDetail.getMeanings() == null || wordDetail.getMeanings().isEmpty()) {
            try {
                String aiResult = aiService.searchWord(q);
                if (aiResult != null && !aiResult.isEmpty() && !aiResult.contains("unavailable")
                        && !aiResult.contains("error") && !aiResult.contains("failed")) {
                    wordDetail = parseAiWordResult(aiResult, q);
                    if (wordDetail != null && wordDetail.getMeanings() != null
                            && !wordDetail.getMeanings().contains("unavailable")) {
                        try {
                            wordDetail.setCreateTime(LocalDateTime.now());
                            wordDetail.setUpdateTime(LocalDateTime.now());
                            wordService.save(wordDetail);
                        } catch (Exception e) {
                            log.warn("保存AI查词结果失败: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("AI查词失败: q='{}', error={}", q, e.getMessage());
            }
        }

        if (wordDetail != null) {
            response.put("wordDetail", wordDetail);
        }

        // 2. 离线词典
        try {
            Map<String, Object> offlineResult = offlineDictService.lookupWord(q.toLowerCase(), null);
            if (offlineResult != null && offlineResult.get("results") != null) {
                Map<String, Object> results = (Map<String, Object>) offlineResult.get("results");
                if (!results.isEmpty()) {
                    response.put("offlineDicts", offlineResult);
                }
            }
        } catch (Exception e) {
            log.warn("离线词典查询失败: q='{}', error={}", q, e.getMessage());
        }

        // 3. 翻译
        try {
            String translatedText = doTranslate(q, "en", "zh");
            if (translatedText != null && !translatedText.isEmpty()) {
                Map<String, String> translationResult = new HashMap<>();
                translationResult.put("result", translatedText);
                translationResult.put("engine", "Baidu");
                response.put("translation", translationResult);
            }
        } catch (Exception e) {
            log.warn("翻译失败: q='{}', error={}", q, e.getMessage());
        }

        response.put("source", "word_search_translate");
    }

    /**
     * 英文句子/短语 → 中文：翻译 + 可选单词分析（并行优化）
     */
    private void handleEnSentenceToZh(String q, Map<String, Object> response) {
        // 并行：百度翻译 ∥ 核心词查询
        CompletableFuture<Void> translationFuture = CompletableFuture.runAsync(() -> {
            try {
                String translatedText = doTranslate(q, "en", "zh");
                if (translatedText != null && !translatedText.isEmpty()) {
                    Map<String, String> translationResult = new HashMap<>();
                    translationResult.put("result", translatedText);
                    translationResult.put("engine", "Baidu");
                    synchronized (response) {
                        response.put("translation", translationResult);
                    }
                }
            } catch (Exception e) {
                log.error("翻译失败: q='{}', error={}", q, e.getMessage());
            }
        });

        CompletableFuture<Void> wordAnalysisFuture = CompletableFuture.runAsync(() -> {
            String[] words = q.split("\\s+");
            List<Map<String, String>> wordAnalysis = new ArrayList<>();
            for (String w : words) {
                String clean = w.replaceAll("[^a-zA-Z'\\-]", "").toLowerCase();
                if (clean.length() < 2)
                    continue;
                try {
                    Word wordInfo = wordService.searchWord(clean);
                    if (wordInfo != null && wordInfo.getMeanings() != null && !wordInfo.getMeanings().isEmpty()) {
                        Map<String, String> entry = new HashMap<>();
                        entry.put("word", clean);
                        entry.put("phonetic", wordInfo.getPhonetic() != null ? wordInfo.getPhonetic() : "");
                        try {
                            JSONArray meaningsArr = JSON.parseArray(wordInfo.getMeanings());
                            if (meaningsArr != null && !meaningsArr.isEmpty()) {
                                JSONObject first = meaningsArr.getJSONObject(0);
                                entry.put("meaning",
                                        (first.getString("partOfSpeech") != null ? first.getString("partOfSpeech") + " "
                                                : "")
                                                + first.getString("definition"));
                            }
                        } catch (Exception ex) {
                            entry.put("meaning", wordInfo.getMeanings());
                        }
                        wordAnalysis.add(entry);
                    }
                } catch (Exception e) {
                    /* 忽略单个单词查询失败 */ }
                if (wordAnalysis.size() >= 5)
                    break;
            }
            if (!wordAnalysis.isEmpty()) {
                synchronized (response) {
                    response.put("wordAnalysis", wordAnalysis);
                }
            }
        });

        try {
            CompletableFuture.allOf(translationFuture, wordAnalysisFuture).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("并行查询部分超时: q='{}', error={}", q, e.getMessage());
        }

        // 翻译完成后，串行调用AI深度解析
        try {
            translationFuture.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("翻译等待超时: {}", e.getMessage());
        }

        Object translationObj = response.get("translation");
        String translatedText = null;
        if (translationObj instanceof Map) {
            translatedText = (String) ((Map<?, ?>) translationObj).get("result");
        }
        if (translatedText != null && !translatedText.isEmpty()) {
            try {
                String analysis = aiService.chatWithoutHistory(
                        "你是考研英语辅导专家，擅长翻译和长难句教学。请对给定的英文句子进行深度解析。\n" +
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
                        "英文原句：" + q + "\n中文翻译：" + translatedText);
                if (analysis != null && !analysis.isEmpty() && !analysis.contains("unavailable")) {
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
                            response.put("section" + (si + 1), sectionContent);
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
                                    response.put("structured", structured);
                                    // 兼容旧字段
                                    JSONObject analysisObj = new JSONObject();
                                    analysisObj.put("translation_direct", translatedText);
                                    analysisObj.put("translation_elegant", "");
                                    analysisObj.put("grammar_tags", structured.getJSONArray("grammarTags"));
                                    analysisObj.put("main_structure", structured.getString("mainStructure"));
                                    analysisObj.put("grammar_analysis", response.get("section3"));
                                    analysisObj.put("core_vocab", structured.getJSONArray("coreVocab"));
                                    analysisObj.put("difficulty", structured.getString("difficulty"));
                                    analysisObj.put("grammar_points", structured.getJSONArray("grammarPoints"));
                                    analysisObj.put("usage_notes", structured.getJSONArray("examRelevance"));
                                    response.put("analysis", analysisObj);
                                }
                            } catch (Exception parseEx) {
                                log.warn("解析结构化JSON失败: {}", parseEx.getMessage());
                            }
                        }
                    }

                    // 回退逻辑
                    if (!response.containsKey("structured")) {
                        String cleaned = analysis.trim();
                        if (cleaned.startsWith("```")) {
                            cleaned = cleaned.replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "")
                                    .replaceAll("\\s*```$", "");
                        }
                        try {
                            JSONObject analysisObj = JSON.parseObject(cleaned);
                            if (analysisObj != null) {
                                response.put("analysis", analysisObj);
                            } else {
                                response.put("analysis", analysis);
                            }
                        } catch (Exception parseEx) {
                            response.put("analysis", analysis);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("英文句子深度解析失败: {}", e.getMessage());
            }

            // 自动保存为长难句
            try {
                Long userId = getUserId();
                if (userId != null) {
                    com.geekyan.entity.LongSentence sentence = new com.geekyan.entity.LongSentence();
                    sentence.setSentence(q);
                    sentence.setTranslation(translatedText);
                    sentence.setSentenceType("analysis");
                    sentence.setSource("AI翻译分析");

                    Object structuredObj = response.get("structured");
                    if (structuredObj instanceof JSONObject) {
                        JSONObject sObj = (JSONObject) structuredObj;
                        sentence.setGrammarTags(sObj.getJSONArray("grammarTags") != null
                                ? sObj.getJSONArray("grammarTags").toJSONString()
                                : null);
                        sentence.setCoreVocab(
                                sObj.getJSONArray("coreVocab") != null ? sObj.getJSONArray("coreVocab").toJSONString()
                                        : null);
                        sentence.setDifficulty(sObj.getString("difficulty"));
                        StringBuilder analysisText = new StringBuilder();
                        // 主干提取
                        String mainStructure = sObj.getString("mainStructure");
                        if (mainStructure != null && !mainStructure.isEmpty()) {
                            analysisText.append("【句子主干】").append(mainStructure).append("\n\n");
                        }
                        for (int si = 1; si <= 4; si++) {
                            Object sec = response.get("section" + si);
                            if (sec != null) {
                                analysisText.append(sec.toString()).append("\n\n");
                            }
                        }
                        if (analysisText.length() > 0) {
                            sentence.setAnalysis(analysisText.toString().trim());
                        }
                    }

                    com.geekyan.entity.LongSentence saved = longSentenceService.saveSentence(userId, sentence);
                    response.put("longSentenceId", saved.getId());
                    response.put("autoSaved", true);
                    log.info("英文句子查询自动保存长难句: userId={}, sentenceId={}", userId, saved.getId());
                }
            } catch (Exception e) {
                log.warn("自动保存长难句失败: {}", e.getMessage());
            }
        }

        response.put("source", "translate_en_to_zh");
    }

    /**
     * 中文词 → 英文：反向查词
     */
    private void handleZhWordToEn(String q, Map<String, Object> response) {
        List<Map<String, Object>> reverseResults = new ArrayList<>();
        Set<String> seenWords = new HashSet<>();

        // 1. 离线词典反向查询（始终执行）
        try {
            List<Map<String, Object>> offlineResults = offlineDictService.reverseSearch(q, 20);
            if (offlineResults != null && !offlineResults.isEmpty()) {
                for (Map<String, Object> item : offlineResults) {
                    String word = item.get("word") != null ? item.get("word").toString() : "";
                    if (word.isEmpty() || seenWords.contains(word.toLowerCase()))
                        continue;
                    seenWords.add(word.toLowerCase());
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("match", item.get("match"));
                    entry.put("word", word);
                    entry.put("definition", item.get("definition") != null ? item.get("definition") : "");
                    entry.put("source", "offline");
                    reverseResults.add(entry);
                }
            }
        } catch (Exception e) {
            log.warn("离线反向查词失败: q='{}', error={}", q, e.getMessage());
        }

        // 2. AI反向查词（始终执行，与离线结果合并去重）
        try {
            String aiResult = aiService.searchChineseWord(q);
            if (aiResult != null && !aiResult.isEmpty() && !aiResult.contains("unavailable")
                    && !aiResult.contains("error")) {
                String cleaned = aiResult.trim();
                if (cleaned.startsWith("```")) {
                    cleaned = cleaned.replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "")
                            .replaceAll("\\s*```$", "");
                }
                try {
                    JSONObject parsed = JSON.parseObject(cleaned);
                    if (parsed != null) {
                        JSONArray englishArr = parsed.getJSONArray("english");
                        if (englishArr != null) {
                            for (int i = 0; i < englishArr.size(); i++) {
                                JSONObject item = englishArr.getJSONObject(i);
                                String word = item.getString("word");
                                if (word == null || word.isEmpty() || seenWords.contains(word.toLowerCase()))
                                    continue;
                                seenWords.add(word.toLowerCase());
                                Map<String, Object> entry = new HashMap<>();
                                entry.put("word", word);
                                entry.put("phonetic", item.getString("phonetic"));
                                entry.put("pos", item.getString("pos"));
                                entry.put("definition", item.getString("def"));
                                entry.put("matchType", item.getString("matchType"));
                                entry.put("source", "ai");
                                reverseResults.add(entry);
                            }
                        }
                        // 提取例句
                        JSONArray examplesArr = parsed.getJSONArray("examples");
                        if (examplesArr != null && !examplesArr.isEmpty()) {
                            List<Map<String, String>> exampleList = new ArrayList<>();
                            for (int i = 0; i < examplesArr.size(); i++) {
                                JSONObject ex = examplesArr.getJSONObject(i);
                                Map<String, String> exMap = new HashMap<>();
                                exMap.put("en", ex.getString("en"));
                                exMap.put("cn", ex.getString("cn"));
                                exampleList.add(exMap);
                            }
                            response.put("examples", exampleList);
                        }
                    }
                } catch (Exception parseEx) {
                    log.warn("解析AI反向查询结果失败: {}", parseEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("AI反向查词失败: q='{}', error={}", q, e.getMessage());
        }

        // 3. 为离线结果补充释义：查询每个英文词的简要中文释义
        for (Map<String, Object> entry : reverseResults) {
            if ("offline".equals(entry.get("source"))
                    && (entry.get("definition") == null || entry.get("definition").toString().isEmpty())) {
                try {
                    String word = entry.get("word").toString();
                    Word cached = wordService.searchWord(word);
                    if (cached != null && cached.getMeanings() != null) {
                        String meanings = cached.getMeanings();
                        try {
                            JSONArray arr = JSON.parseArray(meanings);
                            if (arr != null && !arr.isEmpty()) {
                                JSONObject first = arr.getJSONObject(0);
                                String def = first.getString("definition");
                                if (def != null && !def.isEmpty()) {
                                    entry.put("definition", def);
                                }
                            }
                        } catch (Exception ignored) {
                            if (meanings.length() <= 100) {
                                entry.put("definition", meanings);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // 4. 百度翻译：获取中文→英文翻译
        try {
            String translatedText = doTranslate(q, "zh", "en");
            if (translatedText != null && !translatedText.isEmpty()) {
                Map<String, String> translationResult = new HashMap<>();
                translationResult.put("result", translatedText);
                translationResult.put("engine", "Baidu");
                response.put("translation", translationResult);
            }
        } catch (Exception e) {
            log.warn("百度翻译失败: q='{}', error={}", q, e.getMessage());
        }

        // 5. AI详细分析：获取音标、释义、词源、搭配、例句、同义词等
        try {
            String aiContent = aiService.chatWithoutHistory(
                    "你是专业的英语词典编辑，擅长中文词汇的英文对应词解析。请对给定的中文词进行详细的英文解析。\n" +
                            "请按以下格式输出：\n" +
                            "1. 最对应的英文单词及其音标(英式/美式)\n" +
                            "2. 词性和释义\n" +
                            "3. 词源（如有，如无则写“无”）\n" +
                            "4. 常用搭配\n" +
                            "5. 双语例句（3-5个）\n" +
                            "6. 同义词/反义词\n" +
                            "7. 记忆技巧（必须包含：谐音记忆，如无法谐音则写“无”；词根/联想记忆，如无则写“无”）",
                    "中文词：" + q);
            if (aiContent != null && !aiContent.isEmpty() && !aiContent.contains("unavailable")) {
                response.put("content", aiContent);
            }
        } catch (Exception e) {
            log.warn("AI详细分析失败: q='{}', error={}", q, e.getMessage());
        }

        response.put("reverseResults", reverseResults);
        response.put("totalMatches", reverseResults.size());
        response.put("source", reverseResults.isEmpty() ? "none"
                : (reverseResults.stream().anyMatch(r -> "ai".equals(r.get("source"))) ? "mixed" : "offline"));
    }

    /**
     * 中文句子/短语 → 英文：翻译（并行优化）
     */
    private void handleZhSentenceToEn(String q, Map<String, Object> response) {
        // 并行：百度翻译 ∥ AI语法分析（翻译完成后保存长难句）
        // 第一步：先获取翻译结果（AI语法分析依赖翻译结果）
        String[] englishSentenceHolder = new String[1];

        CompletableFuture<Void> translationFuture = CompletableFuture.runAsync(() -> {
            try {
                String englishSentence = doTranslate(q, "zh", "en");
                if (englishSentence != null && !englishSentence.isEmpty()) {
                    Map<String, String> translationResult = new HashMap<>();
                    translationResult.put("result", englishSentence);
                    translationResult.put("engine", "Baidu");
                    synchronized (response) {
                        response.put("translation", translationResult);
                    }
                    synchronized (englishSentenceHolder) {
                        englishSentenceHolder[0] = englishSentence;
                    }
                }
            } catch (Exception e) {
                log.error("中文翻译英文失败: q='{}', error={}", q, e.getMessage());
                // 降级到AI翻译
                try {
                    String aiResult = aiService.translate(q, "zh", "en");
                    if (aiResult != null && !aiResult.isEmpty() && !aiResult.contains("unavailable")) {
                        Map<String, String> translationResult = new HashMap<>();
                        translationResult.put("result", aiResult);
                        translationResult.put("engine", "AI");
                        synchronized (response) {
                            response.put("translation", translationResult);
                        }
                        synchronized (englishSentenceHolder) {
                            englishSentenceHolder[0] = aiResult;
                        }
                    }
                } catch (Exception ex) {
                    log.error("AI翻译也失败: q='{}', error={}", q, ex.getMessage());
                }
            }
        });

        // 等翻译完成后再做语法分析和保存（有依赖关系）
        try {
            translationFuture.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("翻译超时: q='{}', error={}", q, e.getMessage());
        }

        String englishSentence = englishSentenceHolder[0];

        // 第二步：用英文结果调用AI深度解析（四段式+结构化JSON）
        if (englishSentence != null && !englishSentence.isEmpty()) {
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
                        "原文：" + englishSentence + "\n中文：" + q);
                if (analysis != null && !analysis.isEmpty() && !analysis.contains("unavailable")) {
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
                            response.put("section" + (si + 1), sectionContent);
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
                                    response.put("structured", structured);
                                    // 兼容旧字段
                                    JSONObject analysisObj = new JSONObject();
                                    analysisObj.put("grammar_tags", structured.getJSONArray("grammarTags"));
                                    analysisObj.put("main_structure", structured.getString("mainStructure"));
                                    analysisObj.put("grammar_analysis", response.get("section3"));
                                    analysisObj.put("core_vocab", structured.getJSONArray("coreVocab"));
                                    analysisObj.put("difficulty", structured.getString("difficulty"));
                                    analysisObj.put("grammar_points", structured.getJSONArray("grammarPoints"));
                                    analysisObj.put("usage_notes", structured.getJSONArray("examRelevance"));
                                    response.put("analysis", analysisObj);
                                }
                            } catch (Exception parseEx) {
                                log.warn("解析结构化JSON失败: {}", parseEx.getMessage());
                            }
                        }
                    }

                    // 回退逻辑
                    if (!response.containsKey("structured")) {
                        String cleaned = analysis.trim();
                        if (cleaned.startsWith("```")) {
                            cleaned = cleaned.replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "")
                                    .replaceAll("\\s*```$", "");
                        }
                        try {
                            JSONObject analysisObj = JSON.parseObject(cleaned);
                            if (analysisObj != null) {
                                response.put("analysis", analysisObj);
                            } else {
                                response.put("analysis", analysis);
                            }
                        } catch (Exception parseEx) {
                            response.put("analysis", analysis);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("中文句子深度解析失败: {}", e.getMessage());
            }

            // 第三步：自动保存为长难句 + 创建复习任务
            try {
                Long userId = getUserId();
                if (userId != null) {
                    com.geekyan.entity.LongSentence sentence = new com.geekyan.entity.LongSentence();
                    sentence.setSentence(englishSentence);
                    sentence.setTranslation(q); // 原始中文作为翻译
                    sentence.setSentenceType("analysis");
                    sentence.setSource("中文句子翻译");
                    sentence.setLiteralTranslation(null);
                    sentence.setFreeTranslation(null);

                    // 优先从structured中提取，兼容旧analysis字段
                    Object structuredObj = response.get("structured");
                    Object analysisObj = response.get("analysis");
                    if (structuredObj instanceof JSONObject) {
                        JSONObject sObj = (JSONObject) structuredObj;
                        sentence.setGrammarTags(sObj.getJSONArray("grammarTags") != null
                                ? sObj.getJSONArray("grammarTags").toJSONString()
                                : null);
                        sentence.setCoreVocab(
                                sObj.getJSONArray("coreVocab") != null ? sObj.getJSONArray("coreVocab").toJSONString()
                                        : null);
                        sentence.setDifficulty(sObj.getString("difficulty"));
                        // 四段式文本拼接为analysis
                        StringBuilder analysisText = new StringBuilder();
                        String mainStructure = sObj.getString("mainStructure");
                        if (mainStructure != null && !mainStructure.isEmpty()) {
                            analysisText.append("【句子主干】").append(mainStructure).append("\n\n");
                        }
                        for (int si = 1; si <= 4; si++) {
                            Object sec = response.get("section" + si);
                            if (sec != null) {
                                analysisText.append(sec.toString()).append("\n\n");
                            }
                        }
                        if (analysisText.length() > 0) {
                            sentence.setAnalysis(analysisText.toString().trim());
                        }
                    } else if (analysisObj instanceof JSONObject) {
                        JSONObject aObj = (JSONObject) analysisObj;
                        if (aObj.containsKey("translation_direct")) {
                            sentence.setLiteralTranslation(aObj.getString("translation_direct"));
                        }
                        if (aObj.containsKey("translation_elegant")) {
                            sentence.setFreeTranslation(aObj.getString("translation_elegant"));
                        }
                        if (aObj.containsKey("grammar_tags")) {
                            sentence.setGrammarTags(aObj.getJSONArray("grammar_tags").toJSONString());
                        }
                        if (aObj.containsKey("core_vocab")) {
                            sentence.setCoreVocab(aObj.getJSONArray("core_vocab").toJSONString());
                        }
                        if (aObj.containsKey("difficulty")) {
                            sentence.setDifficulty(aObj.getString("difficulty"));
                        }
                        if (aObj.containsKey("grammar_analysis")) {
                            sentence.setAnalysis(aObj.getString("grammar_analysis"));
                        }
                    } else if (response.containsKey("analysis")) {
                        sentence.setAnalysis((String) response.get("analysis"));
                    }

                    com.geekyan.entity.LongSentence saved = longSentenceService.saveSentence(userId, sentence);
                    response.put("longSentenceId", saved.getId());
                    response.put("autoSaved", true);
                    log.info("中文句子查询自动保存长难句: userId={}, sentenceId={}", userId, saved.getId());
                }
            } catch (Exception e) {
                log.warn("自动保存长难句失败: {}", e.getMessage());
            }
        }

        response.put("source", "translate_zh_to_en");
    }

    /**
     * 英文短语 → 中文：翻译 + 查短语中核心单词（并行优化）
     */
    private void handleEnPhraseToZh(String q, Map<String, Object> response) {
        // 并行：百度翻译 ∥ 离线词典 ∥ 核心词查询
        CompletableFuture<Void> translationFuture = CompletableFuture.runAsync(() -> {
            try {
                String translatedText = doTranslate(q, "en", "zh");
                if (translatedText != null && !translatedText.isEmpty()) {
                    Map<String, String> translationResult = new HashMap<>();
                    translationResult.put("result", translatedText);
                    translationResult.put("engine", "Baidu");
                    synchronized (response) {
                        response.put("translation", translationResult);
                    }
                }
            } catch (Exception e) {
                log.warn("短语翻译失败: q='{}', error={}", q, e.getMessage());
            }
        });

        CompletableFuture<Void> offlineFuture = CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> offlineResult = offlineDictService.lookupWord(q.toLowerCase(), null);
                if (offlineResult != null && offlineResult.get("results") != null) {
                    Map<String, Object> results = (Map<String, Object>) offlineResult.get("results");
                    if (!results.isEmpty()) {
                        synchronized (response) {
                            response.put("offlineDicts", offlineResult);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("离线词典查询短语失败: q='{}', error={}", q, e.getMessage());
            }
        });

        CompletableFuture<Void> wordAnalysisFuture = CompletableFuture.runAsync(() -> {
            String[] words = q.split("\\s+");
            List<Map<String, String>> wordAnalysis = new ArrayList<>();
            for (String w : words) {
                String clean = w.replaceAll("[^a-zA-Z'\\-]", "").toLowerCase();
                if (clean.length() < 2)
                    continue;
                try {
                    Word wordInfo = wordService.searchWord(clean);
                    if (wordInfo != null && wordInfo.getMeanings() != null && !wordInfo.getMeanings().isEmpty()) {
                        Map<String, String> entry = new HashMap<>();
                        entry.put("word", clean);
                        entry.put("phonetic", wordInfo.getPhonetic() != null ? wordInfo.getPhonetic() : "");
                        try {
                            JSONArray meaningsArr = JSON.parseArray(wordInfo.getMeanings());
                            if (meaningsArr != null && !meaningsArr.isEmpty()) {
                                JSONObject first = meaningsArr.getJSONObject(0);
                                entry.put("meaning",
                                        (first.getString("partOfSpeech") != null ? first.getString("partOfSpeech") + " "
                                                : "")
                                                + first.getString("definition"));
                            }
                        } catch (Exception ex) {
                            entry.put("meaning", wordInfo.getMeanings());
                        }
                        wordAnalysis.add(entry);
                    }
                } catch (Exception e) {
                    /* 忽略单个单词查询失败 */ }
                if (wordAnalysis.size() >= 5)
                    break;
            }
            if (!wordAnalysis.isEmpty()) {
                synchronized (response) {
                    response.put("wordAnalysis", wordAnalysis);
                }
            }
        });

        try {
            CompletableFuture.allOf(translationFuture, offlineFuture, wordAnalysisFuture).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("并行查询部分超时: q='{}', error={}", q, e.getMessage());
        }

        // 自动保存英文短语到长难句管理
        try {
            Long userId = getUserId();
            if (userId != null) {
                Object translationObj = response.get("translation");
                String translatedText = null;
                if (translationObj instanceof Map) {
                    translatedText = (String) ((Map<?, ?>) translationObj).get("result");
                }
                if (translatedText != null && !translatedText.isEmpty()) {
                    com.geekyan.entity.LongSentence sentence = new com.geekyan.entity.LongSentence();
                    sentence.setSentence(q);
                    sentence.setTranslation(translatedText);
                    sentence.setSentenceType("phrase");
                    sentence.setSource("英文短语翻译");
                    com.geekyan.entity.LongSentence saved = longSentenceService.saveSentence(userId, sentence);
                    response.put("longSentenceId", saved.getId());
                    response.put("autoSaved", true);
                    log.info("英文短语查询自动保存长难句: userId={}, sentenceId={}", userId, saved.getId());
                }
            }
        } catch (Exception e) {
            log.warn("自动保存长难句失败: {}", e.getMessage());
        }

        response.put("source", "translate_phrase_en_to_zh");
    }

    /**
     * 中文短语 → 英文：翻译 + 反向查词（并行优化）
     */
    private void handleZhPhraseToEn(String q, Map<String, Object> response) {
        // 并行：百度翻译 ∥ 反向查词
        CompletableFuture<Void> translationFuture = CompletableFuture.runAsync(() -> {
            try {
                String translatedText = doTranslate(q, "zh", "en");
                if (translatedText != null && !translatedText.isEmpty()) {
                    Map<String, String> translationResult = new HashMap<>();
                    translationResult.put("result", translatedText);
                    translationResult.put("engine", "Baidu");
                    synchronized (response) {
                        response.put("translation", translationResult);
                    }
                }
            } catch (Exception e) {
                log.warn("中文短语翻译失败: q='{}', error={}", q, e.getMessage());
                try {
                    String aiResult = aiService.translate(q, "zh", "en");
                    if (aiResult != null && !aiResult.isEmpty() && !aiResult.contains("unavailable")) {
                        Map<String, String> translationResult = new HashMap<>();
                        translationResult.put("result", aiResult);
                        translationResult.put("engine", "AI");
                        synchronized (response) {
                            response.put("translation", translationResult);
                        }
                    }
                } catch (Exception ex) {
                    log.error("AI翻译也失败: q='{}', error={}", q, ex.getMessage());
                }
            }
        });

        CompletableFuture<Void> reverseFuture = CompletableFuture.runAsync(() -> {
            List<Map<String, Object>> reverseResults = new ArrayList<>();
            try {
                List<Map<String, Object>> offlineResults = offlineDictService.reverseSearch(q, 10);
                if (offlineResults != null && !offlineResults.isEmpty()) {
                    for (Map<String, Object> item : offlineResults) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("match", item.get("match"));
                        entry.put("word", item.get("word"));
                        entry.put("definition", item.get("definition") != null ? item.get("definition") : "");
                        entry.put("source", "offline");
                        reverseResults.add(entry);
                    }
                }
            } catch (Exception e) {
                log.warn("离线反向查词失败: q='{}', error={}", q, e.getMessage());
            }
            if (!reverseResults.isEmpty()) {
                synchronized (response) {
                    response.put("reverseResults", reverseResults);
                }
            }
        });

        try {
            CompletableFuture.allOf(translationFuture, reverseFuture).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("并行查询部分超时: q='{}', error={}", q, e.getMessage());
        }

        response.put("source", "translate_phrase_zh_to_en");
    }

    /**
     * 解析AI查词结果为Word对象
     */
    private Word parseAiWordResult(String aiResult, String originalWord) {
        try {
            String jsonStr = aiResult;
            if (aiResult.contains("```json")) {
                jsonStr = aiResult.substring(aiResult.indexOf("{"), aiResult.lastIndexOf("}") + 1);
            } else if (aiResult.contains("```")) {
                jsonStr = aiResult.substring(aiResult.indexOf("{"), aiResult.lastIndexOf("}") + 1);
            }
            JSONObject json = JSON.parseObject(jsonStr);
            Word word = new Word();
            word.setWord(json.getString("word"));
            word.setPhonetic(json.getString("phonetic"));
            JSONArray meaningsArr = json.getJSONArray("meanings");
            if (meaningsArr != null)
                word.setMeanings(meaningsArr.toJSONString());
            JSONArray examplesArr = json.getJSONArray("examples");
            if (examplesArr != null)
                word.setExamples(examplesArr.toJSONString());
            word.setEtymology(json.getString("etymology"));
            JSONArray synonymsArr = json.getJSONArray("synonyms");
            if (synonymsArr != null)
                word.setSynonyms(synonymsArr.toJSONString());
            JSONArray antonymsArr = json.getJSONArray("antonyms");
            if (antonymsArr != null)
                word.setAntonyms(antonymsArr.toJSONString());
            JSONObject wordFormsObj = json.getJSONObject("word_forms");
            if (wordFormsObj != null)
                word.setWordForms(wordFormsObj.toJSONString());
            String examTags = json.getString("exam_tags");
            if (examTags != null)
                word.setExamTags(examTags);
            String freqLevel = json.getString("frequency_level");
            if (freqLevel != null)
                word.setFrequencyLevel(freqLevel);
            word.setFrequency(json.getIntValue("frequency") > 0 ? json.getIntValue("frequency") : 50);
            word.setDifficulty(json.getString("difficulty") != null ? json.getString("difficulty") : "medium");
            return word;
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping
    public AjaxResult translate(@RequestBody Map<String, Object> params) {
        String text = (String) params.get("text");
        String from = (String) params.getOrDefault("from", "auto");
        String to = (String) params.getOrDefault("to", "zh");
        Long documentId = params.get("documentId") != null ? Long.valueOf(params.get("documentId").toString()) : null;
        Integer paragraphIndex = params.get("paragraphIndex") != null
                ? Integer.valueOf(params.get("paragraphIndex").toString())
                : null;

        if (text == null || text.isEmpty()) {
            return error("翻译内容不能为空");
        }

        if (documentId != null && paragraphIndex != null) {
            TranslationCache cached = translationCacheService.getByUserDocParagraph(getUserId(), documentId,
                    paragraphIndex);
            if (cached != null && cached.getTranslatedText() != null && !cached.getTranslatedText().isEmpty()) {
                Map<String, Object> data = new HashMap<>();
                data.put("translatedText", cached.getTranslatedText());
                data.put("cached", true);
                return success(data);
            }
        }

        String redisCached = queryCacheService.getTranslate(text, from, to);
        if (redisCached != null && !redisCached.isEmpty()) {
            try {
                if (isUsableTranslation(text, redisCached, from, to)) {
                    Map<String, Object> cachedData = new HashMap<>();
                    cachedData.put("translatedText", redisCached);
                    cachedData.put("cached", true);
                    return success(cachedData);
                }
                queryCacheService.evictTranslate(text, from, to);
                log.warn("翻译缓存无效，已清理: text={}, from={}, to={}, cached={}", text, from, to, redisCached);
            } catch (Exception e) {
                log.warn("解析翻译Redis缓存失败: text={}", text);
            }
        }

        String result = doTranslate(text, from, to);

        if (result != null && !result.isEmpty()) {
            queryCacheService.setTranslate(text, from, to, result);

            if (documentId != null && paragraphIndex != null) {
                try {
                    TranslationCache cache = new TranslationCache();
                    cache.setUserId(getUserId());
                    cache.setDocumentId(documentId);
                    cache.setParagraphIndex(paragraphIndex);
                    cache.setSourceText(text);
                    cache.setTranslatedText(result);
                    cache.setSourceLang(from.equals("auto") ? "en" : from);
                    cache.setTargetLang(to);
                    translationCacheService.saveOrUpdateByParagraph(cache);
                } catch (Exception e) {
                    log.warn("保存翻译缓存失败", e);
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("translatedText", result);
            data.put("cached", false);
            return success(data);
        }

        return error("翻译服务暂不可用，请稍后重试");
    }

    @GetMapping("/cache/{documentId}")
    public AjaxResult getCachedTranslations(@PathVariable Long documentId) {
        List<TranslationCache> caches = translationCacheService.getByUserDoc(getUserId(), documentId);
        Map<Integer, String> translations = new HashMap<>();
        for (TranslationCache cache : caches) {
            if (cache.getTranslatedText() != null && !cache.getTranslatedText().isEmpty()) {
                translations.put(cache.getParagraphIndex(), cache.getTranslatedText());
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("translations", translations);
        data.put("count", translations.size());
        return success(data);
    }

    /**
     * 根据查询结果创建历史记录条目摘要
     * 截断规则：
     * - 英文单词：单词 + 词性 + 第一个释义前8字
     * - 英文短语：原文 + 简短翻译，总字数≤20
     * - 英文句子：原文前20字 + 省略号
     * - 中文单词：原文 → 第一个英文结果
     * - 中文短语/句子：原文 → 译文前15字...
     */
    private Map<String, Object> createHistoryEntry(Map<String, Object> response) {
        Map<String, Object> historyEntry = new HashMap<>();
        String query = (String) response.get("query");
        String queryType = (String) response.get("queryType");

        historyEntry.put("query", query);
        historyEntry.put("queryType", queryType);

        String summary = "";
        try {
            if (queryType != null) {
                switch (queryType) {
                    case "EN_WORD_TO_ZH":
                        Word wordDetail = (Word) response.get("wordDetail");
                        if (wordDetail != null && wordDetail.getMeanings() != null) {
                            JSONArray meanings = JSON.parseArray(wordDetail.getMeanings());
                            if (meanings != null && !meanings.isEmpty()) {
                                JSONObject firstMeaning = meanings.getJSONObject(0);
                                String pos = firstMeaning.getString("partOfSpeech");
                                // 优先取 definitions 数组中的第一个
                                String def = "";
                                JSONArray defs = firstMeaning.getJSONArray("definitions");
                                if (defs != null && !defs.isEmpty()) {
                                    Object firstDef = defs.get(0);
                                    def = firstDef instanceof String ? (String) firstDef
                                            : (firstDef instanceof JSONObject
                                                    ? ((JSONObject) firstDef).getString("definition")
                                                    : "");
                                }
                                if (def == null || def.isEmpty()) {
                                    def = firstMeaning.getString("definition");
                                }
                                if (def != null && !def.isEmpty()) {
                                    summary = (pos != null ? pos + ". " : "")
                                            + (def.length() > 8 ? def.substring(0, 8) + "..." : def);
                                }
                            }
                        }
                        break;
                    case "EN_PHRASE_TO_ZH":
                        // 英文短语：原文 + 简短翻译，总字数≤20
                        Map<String, String> phraseTrans = (Map<String, String>) response.get("translation");
                        if (phraseTrans != null && phraseTrans.get("result") != null) {
                            String trans = phraseTrans.get("result");
                            int maxTransLen = Math.max(0, 20 - query.length() - 1);
                            summary = query + " "
                                    + (trans.length() > maxTransLen ? trans.substring(0, maxTransLen) + "..." : trans);
                            if (summary.length() > 20)
                                summary = summary.substring(0, 20) + "...";
                        }
                        break;
                    case "EN_SENTENCE_TO_ZH":
                        // 英文句子：原文前20字 + 省略号
                        summary = query.length() > 20 ? query.substring(0, 20) + "..." : query;
                        break;
                    case "ZH_WORD_TO_EN":
                        // 中文单词：原文 → 第一个英文结果
                        List<Map<String, Object>> reverseResults = (List<Map<String, Object>>) response
                                .get("reverseResults");
                        if (reverseResults != null && !reverseResults.isEmpty()) {
                            String firstResult = (String) reverseResults.get(0).get("word");
                            if (firstResult != null) {
                                summary = query + " → " + firstResult;
                            }
                        }
                        break;
                    case "ZH_PHRASE_TO_EN":
                    case "ZH_SENTENCE_TO_EN":
                        // 中文短语/句子：原文 → 译文前15字...
                        Map<String, String> zhTrans = (Map<String, String>) response.get("translation");
                        if (zhTrans != null && zhTrans.get("result") != null) {
                            String result = zhTrans.get("result");
                            summary = query + " → " + (result.length() > 15 ? result.substring(0, 15) + "..." : result);
                        }
                        break;
                }
            }
        } catch (Exception e) {
            log.error("创建历史记录摘要失败: query='{}', type='{}', error={}", query, queryType, e.getMessage());
        }

        if (summary == null || summary.isEmpty()) {
            summary = query;
        }

        historyEntry.put("summary", summary);
        return historyEntry;
    }

    private String doTranslate(String text, String from, String to) {
        String result = baiduTranslateService.translate(text, from, to);
        if (isUsableTranslation(text, result, from, to)) {
            return result;
        } else if (result != null && !result.isEmpty()) {
            queryCacheService.evictTranslate(text, from, to);
            log.warn("百度翻译结果无效，改用AI回退: text={}, from={}, to={}, result={}", text, from, to, result);
        }

        try {
            String aiResult = aiService.translate(text, from.equals("auto") ? "en" : from, to);
            if (aiResult != null && !aiResult.isEmpty() && !aiResult.contains("unavailable")
                    && isUsableTranslation(text, aiResult, from, to)) {
                return aiResult;
            }
        } catch (Exception e) {
            log.error("AI翻译失败", e);
        }

        return null;
    }

    private boolean isUsableTranslation(String text, String result, String from, String to) {
        if (result == null || result.trim().isEmpty()) {
            return false;
        }
        String source = normalizeText(text);
        String translated = normalizeText(result);
        if (!source.isEmpty() && source.equalsIgnoreCase(translated) && shouldCrossLanguage(text, from, to)) {
            return false;
        }
        if ("en".equalsIgnoreCase(to) && containsChinese(result)) {
            return false;
        }
        if ("zh".equalsIgnoreCase(to) && containsChinese(text) && containsChinese(result)
                && source.equalsIgnoreCase(translated)) {
            return false;
        }
        return true;
    }

    private boolean shouldCrossLanguage(String text, String from, String to) {
        if (to == null || "auto".equalsIgnoreCase(to)) {
            return false;
        }
        if ("auto".equalsIgnoreCase(from)) {
            return ("en".equalsIgnoreCase(to) && containsChinese(text))
                    || ("zh".equalsIgnoreCase(to) && !containsChinese(text));
        }
        return !from.equalsIgnoreCase(to);
    }

    private boolean containsChinese(String value) {
        return value != null && value.codePoints().anyMatch(c -> c >= 0x4e00 && c <= 0x9fff);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}