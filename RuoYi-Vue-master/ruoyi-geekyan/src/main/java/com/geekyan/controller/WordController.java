package com.geekyan.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geekyan.entity.SearchHistory;
import com.geekyan.entity.Word;
import com.geekyan.entity.WordBook;
import com.geekyan.entity.WordStory;
import com.geekyan.mapper.SearchHistoryMapper;
import com.geekyan.service.IAiService;
import com.geekyan.service.IOfflineDictService;
import com.geekyan.service.IWordBookService;
import com.geekyan.service.IWordService;
import com.geekyan.service.IWordStoryService;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/geekyan/word")
public class WordController extends BaseController {

    @Autowired
    private IWordService wordService;

    @Autowired
    private IWordBookService wordBookService;

    @Autowired
    private IWordStoryService wordStoryService;

    @Autowired
    private IAiService aiService;

    @Autowired
    private IOfflineDictService offlineDictService;

    @Autowired
    private SearchHistoryMapper searchHistoryMapper;

    @Anonymous
    @GetMapping({ "/search", "/detail" })
    public AjaxResult search(@RequestParam String word) {
        if (word == null || word.trim().isEmpty()) {
            return error("单词不能为空");
        }
        String keyword = word.trim();
        Word cached = wordService.searchWord(keyword);
        if (cached != null) {
            return success(cached);
        }

        String aiResult = aiService.searchWord(keyword);
        Word created = parseWord(keyword, aiResult);
        if (created != null) {
            created.setCreateTime(LocalDateTime.now());
            created.setUpdateTime(LocalDateTime.now());
            wordService.save(created);
            return success(created);
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("word", keyword);
        fallback.put("raw", aiResult);
        return success(fallback);
    }

    @Anonymous
    @GetMapping("/offline-search")
    public AjaxResult offlineSearch(@RequestParam String word, @RequestParam(required = false) String dict) {
        if (word == null || word.trim().isEmpty()) {
            return error("单词不能为空");
        }
        return success(offlineDictService.lookupWord(word.trim(), dict));
    }

    @Anonymous
    @GetMapping("/search-chinese")
    public AjaxResult searchChineseWord(@RequestParam String word) {
        if (word == null || word.trim().isEmpty()) {
            return error("查询内容不能为空");
        }
        String result = aiService.searchChineseWord(word.trim());
        return success(parseJsonOrRaw(result));
    }

    @Anonymous
    @GetMapping("/reverse")
    public AjaxResult reverse(@RequestParam String q, @RequestParam(defaultValue = "20") int limit) {
        if (q == null || q.trim().isEmpty()) {
            return error("查询内容不能为空");
        }
        List<Map<String, Object>> offline = offlineDictService.reverseSearch(q.trim(), limit);
        if (offline != null && !offline.isEmpty()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("query", q);
            data.put("results", offline);
            data.put("source", "offline");
            return success(data);
        }
        String result = aiService.searchChineseWord(q.trim());
        return success(parseJsonOrRaw(result));
    }

    @Anonymous
    @GetMapping("/fuzzy")
    public AjaxResult fuzzy(@RequestParam String keyword, @RequestParam(defaultValue = "10") int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return error("关键词不能为空");
        }
        List<Word> local = wordService.fuzzySearch(keyword.trim().toLowerCase(), Math.max(1, Math.min(limit, 50)));
        if (local != null && !local.isEmpty()) {
            return success(local);
        }
        return success(offlineDictService.fuzzySuggest(keyword.trim(), limit));
    }

    @Anonymous
    @GetMapping("/dicts")
    public AjaxResult dicts() {
        return success(offlineDictService.listDicts());
    }

    @GetMapping("/history")
    public AjaxResult history(@RequestParam(defaultValue = "20") int limit) {
        Long userId = getUserId();
        List<SearchHistory> list = searchHistoryMapper.selectList(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId)
                .orderByDesc(SearchHistory::getCreateTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
        return success(list);
    }

    @PostMapping("/history")
    public AjaxResult addHistory(@RequestBody Map<String, Object> params) {
        Long userId = getUserId();
        String keyword = stringValue(params.get("keyword"));
        if (keyword == null || keyword.isEmpty()) {
            return error("关键词不能为空");
        }
        SearchHistory history = new SearchHistory();
        history.setUserId(userId);
        history.setKeyword(keyword);
        history.setQueryType(
                firstNonEmpty(stringValue(params.get("queryType")), stringValue(params.get("search_type"))));
        history.setResultWord(stringValue(params.get("result_word")));
        history.setResultPhonetic(stringValue(params.get("result_phonetic")));
        history.setResultMeaning(stringValue(params.get("result_meaning")));
        history.setCreateTime(LocalDateTime.now());
        history.setUpdateTime(LocalDateTime.now());
        searchHistoryMapper.insert(history);
        return success(history);
    }

    @DeleteMapping("/history")
    public AjaxResult clearHistory() {
        Long userId = getUserId();
        searchHistoryMapper.delete(new LambdaQueryWrapper<SearchHistory>().eq(SearchHistory::getUserId, userId));
        return success();
    }

    @DeleteMapping("/history/{id}")
    public AjaxResult deleteHistory(@PathVariable Long id) {
        Long userId = getUserId();
        searchHistoryMapper.delete(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getId, id)
                .eq(SearchHistory::getUserId, userId));
        return success();
    }

    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String keyword) {
        startPage();
        LambdaQueryWrapper<Word> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(Word::getWord, keyword.trim());
        }
        wrapper.orderByDesc(Word::getCreateTime);
        return getDataTable(wordService.list(wrapper));
    }

    @GetMapping("/book")
    public AjaxResult book(@RequestParam(required = false) String bookName,
            @RequestParam(required = false, defaultValue = "time") String sortBy) {
        return success(wordBookService.getUserWordBook(getUserId(), bookName, sortBy));
    }

    @PostMapping("/book/add")
    public AjaxResult addToBook(@RequestBody Map<String, Object> params) {
        Long wordId = longValue(params.get("word_id"));
        String word = stringValue(params.get("word"));
        String bookName = firstNonEmpty(stringValue(params.get("book_name")), "默认生词本");
        if (word == null || word.isEmpty()) {
            return error("单词不能为空");
        }
        wordBookService.addWord(getUserId(), wordId, word, bookName);
        return success();
    }

    @PostMapping("/book/remove")
    public AjaxResult removeFromBook(@RequestBody Map<String, Object> params) {
        Long wordId = longValue(params.get("word_id"));
        String bookName = firstNonEmpty(stringValue(params.get("book_name")), "默认生词本");
        if (wordId == null) {
            return error("单词ID不能为空");
        }
        wordBookService.removeWord(getUserId(), wordId, bookName);
        return success();
    }

    @GetMapping("/book/names")
    public AjaxResult bookNames() {
        return success(wordBookService.getUserBookNames(getUserId()));
    }

    @PostMapping("/book/rename")
    public AjaxResult renameBook(@RequestBody Map<String, Object> params) {
        String oldName = stringValue(params.get("old_name"));
        String newName = stringValue(params.get("new_name"));
        if (oldName == null || oldName.isEmpty() || newName == null || newName.isEmpty()) {
            return error("单词本名称不能为空");
        }
        wordBookService.renameBook(getUserId(), oldName, newName);
        return success();
    }

    @DeleteMapping("/book/{bookName}")
    public AjaxResult deleteBook(@PathVariable String bookName) {
        wordBookService.deleteBook(getUserId(), bookName);
        return success();
    }

    @PostMapping("/book/set-default")
    public AjaxResult setDefaultBook(@RequestBody Map<String, Object> params) {
        String bookName = stringValue(params.get("book_name"));
        if (bookName == null || bookName.isEmpty()) {
            return error("单词本名称不能为空");
        }
        wordBookService.setDefaultBook(getUserId(), bookName);
        return success();
    }

    @Anonymous
    @GetMapping("/mnemonic")
    public AjaxResult mnemonic(@RequestParam String word) {
        if (word == null || word.trim().isEmpty()) {
            return error("单词不能为空");
        }
        String prompt = "你是考研英语单词记忆助手。请为单词\"" + word.trim()
                + "\"生成助记，严格按以下格式输出（每项一行，不要用编号列表以外的格式）：\n"
                + "核心释义：一句话概括最常见含义\n"
                + "谐音记忆：根据发音联想中文谐音，如无法谐音则写“无”\n"
                + "词根/联想：词根词缀拆解或场景联想，如无法拆解则写“无”\n"
                + "短例句：一个简短地道的例句（英文+中文翻译）\n"
                + "注意：不要输出JSON、不要输出多余标题、不要输出追审格式，只输出纯文本。总字数不超过150字。";
        return success(aiService.chatWithoutHistory(prompt, "生成助记"));
    }

    @Anonymous
    @GetMapping("/translate-phrase")
    public AjaxResult translatePhrase(@RequestParam String text, @RequestParam(defaultValue = "zh") String to) {
        if (text == null || text.trim().isEmpty()) {
            return error("文本不能为空");
        }
        String target = "en".equalsIgnoreCase(to) ? "English" : "Chinese";
        String result = aiService.chatWithoutHistory("你是专业翻译助手。只返回译文，不要解释。",
                "Translate to " + target + ": " + text.trim());
        return success(result);
    }

    @PostMapping("/story-mnemonic")
    public AjaxResult storyMnemonic(@RequestBody Map<String, Object> params) {
        Long userId = getUserId();
        List<String> words = toStringList(params.get("words"));
        String bookName = firstNonEmpty(stringValue(params.get("bookName")), "默认生词本");
        if (words.isEmpty()) {
            return error("单词列表不能为空");
        }
        String systemPrompt = "你是考研英语写作辅导专家。请用给定单词创作一段自然短文，只返回JSON："
                + "{\"title\":\"标题\",\"content\":\"正文\",\"wordCount\":使用单词数,\"totalWords\":总词数}";
        String aiResult = aiService.chatWithoutHistory(systemPrompt, "单词：" + String.join(", ", words));
        JSONObject json = extractJsonObject(aiResult);
        String title = json != null ? json.getString("title") : "Word Story";
        String content = json != null ? json.getString("content") : aiResult;
        Integer wordCount = json != null ? json.getInteger("wordCount") : words.size();
        Integer totalWords = json != null ? json.getInteger("totalWords") : null;
        WordStory story = wordStoryService.createStory(userId, title, content, JSON.toJSONString(words), bookName,
                wordCount, totalWords);
        return success(story);
    }

    @GetMapping("/stories")
    public AjaxResult stories(@RequestParam(required = false) String bookName) {
        return success(wordStoryService.getUserStories(getUserId(), bookName));
    }

    @DeleteMapping("/stories/{id}")
    public AjaxResult deleteStory(@PathVariable Long id) {
        wordStoryService.deleteStory(getUserId(), id);
        return success();
    }

    private Word parseWord(String keyword, String aiResult) {
        JSONObject json = extractJsonObject(aiResult);
        if (json == null) {
            return null;
        }
        Word word = new Word();
        word.setWord(firstNonEmpty(json.getString("word"), keyword).toLowerCase());
        word.setPhonetic(json.getString("phonetic"));
        word.setMeanings(toJsonString(json.get("meanings")));
        word.setExamples(toJsonString(json.get("examples")));
        word.setEtymology(json.getString("etymology"));
        word.setSynonyms(toJsonString(json.get("synonyms")));
        word.setAntonyms(toJsonString(json.get("antonyms")));
        word.setWordForms(toJsonString(json.get("word_forms")));
        word.setExamTags(json.getString("exam_tags"));
        word.setFrequencyLevel(json.getString("frequency_level"));
        word.setDifficulty(json.getString("difficulty"));
        Integer frequency = json.getInteger("frequency");
        word.setFrequency(frequency != null ? frequency : 0);
        return word;
    }

    private Object parseJsonOrRaw(String value) {
        JSONObject object = extractJsonObject(value);
        if (object != null) {
            return object;
        }
        try {
            return JSON.parseArray(value);
        } catch (Exception ignored) {
        }
        return value;
    }

    private JSONObject extractJsonObject(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String text = value.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        try {
            return JSON.parseObject(text);
        } catch (Exception e) {
            return null;
        }
    }

    private String toJsonString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return JSON.toJSONString(value);
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value).trim() : null;
    }

    private String firstNonEmpty(String first, String fallback) {
        return first != null && !first.trim().isEmpty() ? first.trim() : fallback;
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> toStringList(Object value) {
        if (value instanceof Collection<?>) {
            return ((Collection<?>) value).stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        if (value instanceof JSONArray) {
            return ((JSONArray) value).stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        if (value instanceof String) {
            return Arrays.stream(((String) value).split("[,，;；、\\s]+"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
