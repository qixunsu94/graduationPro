package com.geekyan.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.geekyan.service.IOfflineDictService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class OfflineDictServiceImpl implements IOfflineDictService {

    private static final Logger log = LoggerFactory.getLogger(OfflineDictServiceImpl.class);

    @Value("${geekyan.dict.service-url}")
    private String dictServiceUrl;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    private final Map<String, String> cssCache = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> lookupWord(String word) {
        return lookupWord(word, null);
    }

    @Override
    public Map<String, Object> lookupWord(String word, String dictName) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (dictName != null && !dictName.isEmpty()) {
                // 查询指定词典
                return lookupSingleDict(word, dictName);
            }
            // 不指定词典时，逐个查询所有可用词典
            List<Map<String, Object>> allDicts = listDicts();
            JSONObject allResults = new JSONObject();
            for (Map<String, Object> dict : allDicts) {
                String name = (String) dict.get("name");
                try {
                    Map<String, Object> single = lookupSingleDict(word, name);
                    if (single != null && single.get("results") != null) {
                        JSONObject singleResults = (JSONObject) single.get("results");
                        if (singleResults.containsKey(name)) {
                            allResults.put(name, singleResults.get(name));
                        }
                    }
                } catch (Exception e) {
                    log.warn("查询词典 {} 失败: {}", name, e.getMessage());
                }
            }
            result.put("word", word);
            result.put("results", allResults);
            return result;
        } catch (IOException e) {
            log.warn("离线词典查询失败: word={}, error={}", word, e.getMessage());
        }
        result.put("word", word);
        result.put("results", new HashMap<>());
        return result;
    }

    private Map<String, Object> lookupSingleDict(String word, String dictName) throws IOException {
        StringBuilder urlBuilder = new StringBuilder(
                dictServiceUrl + "/search?word=" + URLEncoder.encode(word, "UTF-8"));
        if (dictName != null && !dictName.isEmpty()) {
            urlBuilder.append("&dict=").append(URLEncoder.encode(dictName, "UTF-8"));
        }
        Request request = new Request.Builder().url(urlBuilder.toString()).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                JSONObject json = JSON.parseObject(body);
                Map<String, Object> result = new HashMap<>();
                result.put("word", json.getString("word"));
                JSONObject results = json.getJSONObject("results");
                if (results != null) {
                    JSONObject processedResults = new JSONObject();
                    for (String key : results.keySet()) {
                        String html = results.getString(key);
                        if (html != null && !html.trim().isEmpty()) {
                            // 使用适配器模式解析为结构化数据
                            Map<String, Object> structured = parseHtmlContent(html, key);
                            processedResults.put(key, structured);
                        }
                    }
                    result.put("results", processedResults);
                } else {
                    result.put("results", new HashMap<>());
                }
                return result;
            }
        }
        return null;
    }

    @Override
    public List<Map<String, Object>> fuzzySuggest(String keyword, int limit) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        try {
            String url = dictServiceUrl + "/fuzzy?keyword=" + URLEncoder.encode(keyword, "UTF-8") + "&limit=" + limit;
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JSONObject json = JSON.parseObject(body);
                    JSONArray arr = json.getJSONArray("suggestions");
                    if (arr != null) {
                        for (int i = 0; i < arr.size(); i++) {
                            JSONObject item = arr.getJSONObject(i);
                            Map<String, Object> map = new HashMap<>();
                            map.put("word", item.getString("word"));
                            map.put("meaning", item.getString("meaning"));
                            map.put("dict", item.getString("dict"));
                            suggestions.add(map);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("离线词典模糊查询失败: keyword={}, error={}", keyword, e.getMessage());
        }
        return suggestions;
    }

    @Override
    public List<Map<String, Object>> reverseSearch(String chinese, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            String url = dictServiceUrl + "/reverse?q=" + URLEncoder.encode(chinese, "UTF-8") + "&limit=" + limit;
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JSONObject json = JSON.parseObject(body);
                    JSONArray arr = json.getJSONArray("results");
                    if (arr != null) {
                        for (int i = 0; i < arr.size(); i++) {
                            JSONObject item = arr.getJSONObject(i);
                            Map<String, Object> map = new HashMap<>();
                            map.put("word", item.getString("word"));
                            map.put("match", item.getString("match"));
                            results.add(map);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("离线词典反向查询失败: q={}, error={}", chinese, e.getMessage());
        }
        return results;
    }

    @Override
    public List<Map<String, Object>> listDicts() {
        List<Map<String, Object>> dicts = new ArrayList<>();
        try {
            String url = dictServiceUrl + "/dicts";
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JSONObject json = JSON.parseObject(body);
                    JSONArray arr = json.getJSONArray("dicts");
                    if (arr != null) {
                        for (int i = 0; i < arr.size(); i++) {
                            JSONObject item = arr.getJSONObject(i);
                            Map<String, Object> map = new HashMap<>();
                            map.put("name", item.getString("name"));
                            map.put("folder", item.getString("folder"));
                            map.put("entryCount", item.getInteger("entry_count"));
                            map.put("hasReverse", item.getBoolean("has_reverse"));
                            dicts.add(map);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("获取词典列表失败: error={}", e.getMessage());
        }
        return dicts;
    }

    // ==================== 适配器模式：词典结构化解析 ====================

    /**
     * 适配器调度：根据词典名称路由到对应的解析器
     */
    private Map<String, Object> parseHtmlContent(String html, String dictName) {
        try {
            String lower = dictName.toLowerCase();
            if (lower.contains("ldoce5")) {
                return parseLdoce5(html);
            } else if (lower.contains("oald")) {
                return parseOald(html);
            } else if (lower.contains("vocabulary")) {
                return parseVocabularyCom(html);
            } else if (lower.contains("thesaurus")) {
                return parseThesaurus(html);
            }
            return parseDefault(html);
        } catch (Exception e) {
            log.warn("结构化解析词典 {} 失败，回退到默认解析: {}", dictName, e.getMessage());
            return parseDefault(html);
        }
    }

    /**
     * LDOCE5 (朗文当代高级英语辞典第五版) 结构化解析
     */
    private Map<String, Object> parseLdoce5(String html) {
        Map<String, Object> data = new HashMap<>();
        Document doc = Jsoup.parse(html);
        doc.select("script, style, link, meta").remove();
        data.put("dictType", "LDOCE5");

        // 1. 解析词族
        Element wordfams = doc.selectFirst(".wordfams .LDOCE_word_family");
        if (wordfams != null) {
            List<Map<String, String>> familyList = new ArrayList<>();
            Elements children = wordfams.children();
            String currentPos = "";
            for (Element child : children) {
                if (child.hasClass("pos")) {
                    currentPos = child.text();
                } else if (child.is("a.crossRef.w") || child.is("span.w")) {
                    Map<String, String> member = new HashMap<>();
                    member.put("pos", currentPos);
                    member.put("word", child.text());
                    familyList.add(member);
                }
            }
            if (!familyList.isEmpty()) {
                data.put("wordFamily", familyList);
            }
        }

        // 2. 解析核心词条
        Element entry = doc.selectFirst(".ldoceEntry.Entry");
        if (entry == null) {
            entry = doc.selectFirst(".Entry");
        }
        if (entry != null) {
            // 词头
            Element hwd = entry.selectFirst(".HWD");
            if (hwd != null) {
                data.put("headword", hwd.text());
            }

            // 音标
            Elements pronCodes = entry.select(".PronCodes");
            if (!pronCodes.isEmpty()) {
                List<String> pronTexts = new ArrayList<>();
                pronCodes.forEach(p -> pronTexts.add(p.text()));
                data.put("pronunciations", pronTexts);
            }

            // 3. 深度解析义项
            Elements senses = entry.select(".Sense");
            if (!senses.isEmpty()) {
                List<Map<String, Object>> senseList = new ArrayList<>();
                for (Element sense : senses) {
                    Map<String, Object> senseData = new HashMap<>();

                    // 义项编号
                    Element senseNum = sense.selectFirst(".sensenum");
                    if (senseNum != null) {
                        senseData.put("number", senseNum.text());
                    }

                    // 语法说明
                    Element grammar = sense.selectFirst(".GRAM");
                    if (grammar != null) {
                        senseData.put("grammar", grammar.text());
                    }

                    // 释义 - LDOCE5 使用 .DEF 类名
                    Elements defs = sense.select(".DEF");
                    if (!defs.isEmpty()) {
                        // 取第一个 .DEF 的英文释义（排除中文翻译 .cn_txt）
                        StringBuilder defText = new StringBuilder();
                        for (Element def : defs) {
                            // 克隆后移除中文翻译部分
                            Element defClone = def.clone();
                            defClone.select(".cn_txt").remove();
                            String text = defClone.text().trim();
                            if (!text.isEmpty()) {
                                if (defText.length() > 0)
                                    defText.append("; ");
                                defText.append(text);
                            }
                        }
                        if (defText.length() > 0) {
                            senseData.put("definition", defText.toString());
                        }
                        // 提取中文翻译
                        StringBuilder cnText = new StringBuilder();
                        for (Element def : defs) {
                            Elements cnEls = def.select(".cn_txt");
                            for (Element cn : cnEls) {
                                String t = cn.text().trim();
                                if (!t.isEmpty()) {
                                    if (cnText.length() > 0)
                                        cnText.append("; ");
                                    cnText.append(t);
                                }
                            }
                        }
                        if (cnText.length() > 0) {
                            senseData.put("definitionCn", cnText.toString());
                        }
                    }

                    // 例句 - LDOCE5 使用 .EXAMPLE 类名
                    Elements examples = sense.select(".EXAMPLE");
                    if (!examples.isEmpty()) {
                        List<Map<String, String>> exampleList = new ArrayList<>();
                        for (Element ex : examples) {
                            Map<String, String> exMap = new HashMap<>();
                            // 英文例句（排除中文翻译）
                            Element enEl = ex.selectFirst(".english");
                            if (enEl != null) {
                                Element enClone = enEl.clone();
                                enClone.select(".cn_txt").remove();
                                enClone.select(".COLLOINEXA").wrap("<span></span>"); // 保留搭配词
                                exMap.put("sentence", enClone.text().trim());
                            } else {
                                Element exClone = ex.clone();
                                exClone.select(".cn_txt").remove();
                                exClone.select("a.speaker").remove();
                                exMap.put("sentence", exClone.text().trim());
                            }
                            // 中文翻译
                            Element cnEl = ex.selectFirst(".cn_txt");
                            if (cnEl != null) {
                                exMap.put("translation", cnEl.text().trim());
                            }
                            if (!exMap.get("sentence").isEmpty()) {
                                exampleList.add(exMap);
                            }
                        }
                        senseData.put("examples", exampleList);
                    }

                    // 搭配例句 .GramExa
                    Elements gramExas = sense.select(".GramExa");
                    if (!gramExas.isEmpty()) {
                        List<Map<String, String>> gramList = new ArrayList<>();
                        for (Element ge : gramExas) {
                            Map<String, String> gMap = new HashMap<>();
                            Element propform = ge.selectFirst(".PROPFORM");
                            if (propform != null)
                                gMap.put("pattern", propform.text());
                            Element exEl = ge.selectFirst(".EXAMPLE .english");
                            if (exEl != null) {
                                Element exClone = exEl.clone();
                                exClone.select(".cn_txt").remove();
                                gMap.put("example", exClone.text().trim());
                            }
                            if (!gMap.isEmpty())
                                gramList.add(gMap);
                        }
                        if (!gramList.isEmpty())
                            senseData.put("grammarExamples", gramList);
                    }

                    // 近义/反义
                    Element opp = sense.selectFirst(".OPP");
                    if (opp != null) {
                        Element oppClone = opp.clone();
                        oppClone.select(".synopp").remove();
                        senseData.put("opposite", oppClone.text().trim());
                    }

                    if (!senseData.isEmpty()) {
                        senseList.add(senseData);
                    }
                }
                data.put("senses", senseList);
            }

            // Thesaurus 框
            Elements thesBoxes = entry.select(".ThesBox");
            if (!thesBoxes.isEmpty()) {
                List<Map<String, Object>> thesList = new ArrayList<>();
                for (Element tb : thesBoxes) {
                    Map<String, Object> thesData = new HashMap<>();
                    Elements sections = tb.select(".Section");
                    if (!sections.isEmpty()) {
                        for (Element sec : sections) {
                            Element exp = sec.selectFirst(".Exponent .EXP");
                            if (exp != null)
                                thesData.put("word", exp.text());
                            Element secDef = sec.selectFirst(".DEF");
                            if (secDef != null)
                                thesData.put("meaning", secDef.text());
                            Elements secExamples = sec.select(".EXAMPLE");
                            if (!secExamples.isEmpty()) {
                                List<String> exList = new ArrayList<>();
                                secExamples.forEach(e -> {
                                    Element enEl = e.selectFirst(".english");
                                    String t = enEl != null ? enEl.text() : e.text();
                                    if (!t.isEmpty())
                                        exList.add(t);
                                });
                                thesData.put("examples", exList);
                            }
                        }
                    }
                    if (!thesData.isEmpty())
                        thesList.add(thesData);
                }
                if (!thesList.isEmpty())
                    data.put("thesaurus", thesList);
            }
        }

        // 4. 如果没有解析出结构化数据，回退到 cleanedHtml
        if (!data.containsKey("senses") && !data.containsKey("headword")) {
            data.put("cleanedHtml", processHtmlForMiniProgram(html));
        }

        return data;
    }

    /**
     * OALD (牛津高阶英语词典) 结构化解析
     */
    private Map<String, Object> parseOald(String html) {
        Map<String, Object> data = new HashMap<>();
        Document doc = Jsoup.parse(html);
        doc.select("script, style, link, meta").remove();
        data.put("dictType", "OALD");

        // OALD 使用 oald_entry 等类名
        Element entry = doc.selectFirst(".oald_entry, .entry");
        if (entry == null) {
            entry = doc.body();
        }

        if (entry != null) {
            // 词头
            Element hwd = entry.selectFirst(".hwd, .headword");
            if (hwd != null) {
                data.put("headword", hwd.text());
            }

            // 音标
            Elements phonetics = entry.select(".phonetics, .pron, .phon");
            if (!phonetics.isEmpty()) {
                List<String> pronList = new ArrayList<>();
                phonetics.forEach(p -> pronList.add(p.text()));
                data.put("pronunciations", pronList);
            }

            // 词性 + 释义
            Elements senses = entry.select(".sense, .sd-g, .sense-g");
            if (!senses.isEmpty()) {
                List<Map<String, Object>> senseList = new ArrayList<>();
                for (Element sense : senses) {
                    Map<String, Object> senseData = new HashMap<>();

                    Element def = sense.selectFirst(".def, .definition");
                    if (def != null) {
                        senseData.put("definition", def.text());
                    }

                    Element pos = sense.selectFirst(".pos, .pg");
                    if (pos != null) {
                        senseData.put("pos", pos.text());
                    }

                    Elements examples = sense.select(".x, .example, .exg");
                    if (!examples.isEmpty()) {
                        List<String> exList = new ArrayList<>();
                        examples.forEach(e -> exList.add(e.text()));
                        senseData.put("examples", exList);
                    }

                    if (!senseData.isEmpty()) {
                        senseList.add(senseData);
                    }
                }
                data.put("senses", senseList);
            }

            // 词族 / 派生词
            Elements derivatives = entry.select(".derivative, .drv-g");
            if (!derivatives.isEmpty()) {
                List<String> derivList = new ArrayList<>();
                derivatives.forEach(d -> derivList.add(d.text()));
                data.put("derivatives", derivList);
            }
        }

        if (!data.containsKey("senses") && !data.containsKey("headword")) {
            data.put("cleanedHtml", processHtmlForMiniProgram(html));
        }

        return data;
    }

    /**
     * Vocabulary.com 结构化解析
     */
    private Map<String, Object> parseVocabularyCom(String html) {
        Map<String, Object> data = new HashMap<>();
        Document doc = Jsoup.parse(html);
        doc.select("script, style, link, meta").remove();
        data.put("dictType", "Vocabulary");

        // 词头
        Element hwd = doc.selectFirst("#hdr-word-area, .word-area h1, h1");
        if (hwd != null) {
            data.put("headword", hwd.text().trim());
        }

        // 音标
        Elements ipas = doc.select(".ipa-section .span-replace-h3");
        if (!ipas.isEmpty()) {
            List<String> pronList = new ArrayList<>();
            ipas.forEach(p -> {
                String t = p.text().trim();
                if (!t.isEmpty())
                    pronList.add(t);
            });
            if (!pronList.isEmpty())
                data.put("pronunciations", pronList);
        }

        // 简短释义
        Element shortDef = doc.selectFirst("p.short");
        if (shortDef != null) {
            data.put("shortDefinition", shortDef.text().trim());
        }

        // 详细释义
        Element longDef = doc.selectFirst("p.long");
        if (longDef != null) {
            data.put("longDefinition", longDef.text().trim());
        }

        // 词形变化
        Element wordForms = doc.selectFirst("p.word-forms");
        if (wordForms != null) {
            data.put("wordForms", wordForms.text().trim());
        }

        // 义项列表
        Elements senses = doc.select(".sense");
        if (!senses.isEmpty()) {
            List<Map<String, Object>> senseList = new ArrayList<>();
            for (Element sense : senses) {
                Map<String, Object> senseData = new HashMap<>();

                // 词性
                Element posIcon = sense.selectFirst(".pos-icon");
                if (posIcon != null) {
                    senseData.put("pos", posIcon.text().trim());
                }

                // 释义
                Element defEl = sense.selectFirst(":root > .definition");
                if (defEl != null) {
                    Element defClone = defEl.clone();
                    defClone.select(".pos-icon").remove();
                    senseData.put("definition", defClone.text().trim());
                }

                // 例句
                Elements examples = sense.select(".defContent .example");
                if (!examples.isEmpty()) {
                    List<String> exList = new ArrayList<>();
                    examples.forEach(e -> exList.add(e.text().trim()));
                    senseData.put("examples", exList);
                }

                // 同义词
                Elements synLinks = sense.select(".defContent .div-replace-dl.instances a.word");
                if (!synLinks.isEmpty()) {
                    List<Map<String, String>> synList = new ArrayList<>();
                    for (Element synLink : synLinks) {
                        Map<String, String> synMap = new HashMap<>();
                        synMap.put("word", synLink.text().trim());
                        // 同义词的释义
                        Element synDef = synLink.nextElementSibling();
                        if (synDef != null && synDef.is(".definition")) {
                            synMap.put("definition", synDef.text().trim());
                        }
                        // 也可能在父级兄弟中
                        if (synMap.get("definition") == null) {
                            Element parent = synLink.parent();
                            if (parent != null) {
                                Element parentDef = parent.selectFirst(".definition");
                                if (parentDef != null)
                                    synMap.put("definition", parentDef.text().trim());
                            }
                        }
                        synList.add(synMap);
                    }
                    senseData.put("synonyms", synList);
                }

                if (!senseData.isEmpty()) {
                    senseList.add(senseData);
                }
            }
            data.put("senses", senseList);
        }

        if (!data.containsKey("senses") && !data.containsKey("headword")) {
            data.put("cleanedHtml", processHtmlForMiniProgram(html));
        }

        return data;
    }

    /**
     * Thesaurus 结构化解析
     */
    private Map<String, Object> parseThesaurus(String html) {
        Map<String, Object> data = new HashMap<>();
        Document doc = Jsoup.parse(html);
        doc.select("script, style, link, meta").remove();
        data.put("dictType", "Thesaurus");

        // 词头
        Element hwd = doc.selectFirst(".thes h1, h1");
        if (hwd != null) {
            data.put("headword", hwd.text());
        }

        // Thesaurus 使用 css-xxxxx 动态类名，需要用更通用的选择器
        // 导航标签中的词性+含义摘要
        Elements navItems = doc.select(".tabbed-nav ul li");
        if (!navItems.isEmpty()) {
            List<Map<String, String>> categories = new ArrayList<>();
            for (Element nav : navItems) {
                Map<String, String> cat = new HashMap<>();
                Element em = nav.selectFirst("em");
                Element strong = nav.selectFirst("strong");
                if (em != null)
                    cat.put("pos", em.text());
                if (strong != null)
                    cat.put("summary", strong.text());
                if (!cat.isEmpty())
                    categories.add(cat);
            }
            if (!categories.isEmpty())
                data.put("categories", categories);
        }

        // 同义词列表 - 从 ul 列表中提取
        Elements synLists = doc.select("ul[class*='css'] li a[href*='entry']");
        if (!synLists.isEmpty()) {
            List<String> synList = new ArrayList<>();
            synLists.forEach(s -> {
                String t = s.text().trim();
                if (!t.isEmpty() && !synList.contains(t))
                    synList.add(t);
            });
            if (!synList.isEmpty())
                data.put("synonyms", synList);
        }

        // 同义词组标题 (h2 包含 "Synonyms for xxx")
        Elements synHeaders = doc.select("h2");
        if (!synHeaders.isEmpty()) {
            List<Map<String, Object>> synGroups = new ArrayList<>();
            for (Element header : synHeaders) {
                if (!header.text().contains("Synonyms"))
                    continue;
                Map<String, Object> group = new HashMap<>();
                // 摘要信息
                Element summary = header.parent() != null ? header.parent().selectFirst("span") : null;
                if (summary != null) {
                    String summaryText = summary.text().trim();
                    if (!summaryText.isEmpty())
                        group.put("summary", summaryText);
                }
                // 同义词列表
                Element nextUl = header.nextElementSibling();
                if (nextUl != null && nextUl.is("ul")) {
                    Elements links = nextUl.select("li a[href*='entry']");
                    if (!links.isEmpty()) {
                        List<String> syns = new ArrayList<>();
                        links.forEach(l -> {
                            String t = l.text().trim();
                            if (!t.isEmpty() && !syns.contains(t))
                                syns.add(t);
                        });
                        group.put("synonyms", syns);
                    }
                }
                if (!group.isEmpty())
                    synGroups.add(group);
            }
            if (!synGroups.isEmpty())
                data.put("synonymGroups", synGroups);
        }

        if (!data.containsKey("synonyms") && !data.containsKey("synonymGroups") && !data.containsKey("headword")) {
            data.put("cleanedHtml", processHtmlForMiniProgram(html));
        }

        return data;
    }

    /**
     * 默认解析：提取基本文本信息 + 清理后的 HTML
     */
    private Map<String, Object> parseDefault(String html) {
        Map<String, Object> data = new HashMap<>();
        Document doc = Jsoup.parse(html);
        doc.select("script, style, link, meta").remove();
        data.put("dictType", "Default");

        // 尝试提取词头
        Element hwd = doc.selectFirst(".headword, .hwd, .HWD, h1, h2");
        if (hwd != null) {
            data.put("headword", hwd.text());
        }

        // 尝试提取释义
        Elements defs = doc.select(".definition, .def, .Definition, .sense");
        if (!defs.isEmpty()) {
            List<String> defList = new ArrayList<>();
            defs.forEach(d -> {
                String t = d.text().trim();
                if (!t.isEmpty() && t.length() < 500)
                    defList.add(t);
            });
            if (!defList.isEmpty()) {
                data.put("definitions", defList);
            }
        }

        // 同时保留清理后的 HTML 作为兜底
        data.put("cleanedHtml", processHtmlForMiniProgram(html));

        return data;
    }

    private static final Safelist RICH_TEXT_SAFELIST = Safelist.none()
            // 块级元素
            .addTags("div", "p", "h1", "h2", "h3", "h4", "h5", "h6",
                    "ol", "ul", "li", "blockquote", "pre", "hr",
                    "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption",
                    "header", "footer", "section", "article", "aside", "nav")
            // 行内元素
            .addTags("span", "a", "b", "br", "cite", "code", "em", "i",
                    "mark", "q", "s", "strong", "sub", "sup", "u")
            // 媒体
            .addTags("img")
            // 只允许 rich-text 实际支持的属性：style, href, src, alt
            .addAttributes(":all", "style")
            .addAttributes("a", "href")
            .addAttributes("img", "src", "alt")
            // 保留协议：允许 http/https 链接
            .addProtocols("a", "href", "http", "https")
            .addProtocols("img", "src", "http", "https");

    private String processHtmlForMiniProgram(String html) {
        try {
            // 1. 先用 Jsoup 解析，提取 body 内容
            Document doc = Jsoup.parse(html);

            // 2. 在清理前，先为关键元素添加内联样式（因为 class 属性会被移除）
            Element body = doc.body();
            if (body != null) {
                for (Element def : body.select(".definition")) {
                    def.attr("style", "margin:4px 0;color:#333;");
                }
                for (Element ex : body.select(".example")) {
                    ex.attr("style", "margin:4px 0;color:#666;font-style:italic;");
                }
                for (Element pos : body.select(".pos, .gram")) {
                    pos.attr("style", "color:#0066cc;font-weight:bold;margin-right:4px;");
                }
                for (Element headword : body.select(".headword, .hw")) {
                    headword.attr("style", "font-weight:bold;font-size:16px;color:#000;");
                }
                for (Element sense : body.select(".sense")) {
                    sense.attr("style", "margin:8px 0;padding-left:8px;border-left:2px solid #ddd;");
                }
                for (Element phon : body.select(".phonetic, .pron")) {
                    phon.attr("style", "color:#888;");
                }
                for (Element img : body.select("img")) {
                    String src = img.attr("src");
                    if (src.startsWith("/static/")) {
                        img.attr("src", dictServiceUrl + src);
                    }
                    img.attr("style", "max-width:100%;height:auto;");
                }
                // 所有 a 标签的 href 设为 #，防止跳转
                for (Element a : body.select("a")) {
                    a.attr("href", "#");
                }
            }

            // 3. 用 Safelist 严格清理：移除所有不在白名单的标签和属性
            // 这会彻底移除 class, id, data-*, on*, 自定义属性等
            String bodyHtml = body != null ? body.html() : html;
            String cleanedHtml = Jsoup.clean(bodyHtml, RICH_TEXT_SAFELIST);

            return cleanedHtml;
        } catch (Exception e) {
            log.warn("Jsoup清理HTML失败: {}", e.getMessage());
            // 最终回退：只保留纯文本
            return Jsoup.clean(html, Safelist.none());
        }
    }

    private String fetchCssContent(String cssFileName) {
        if (cssCache.containsKey(cssFileName)) {
            return cssCache.get(cssFileName);
        }
        try {
            String url = dictServiceUrl + "/static/" + URLEncoder.encode(cssFileName, StandardCharsets.UTF_8.name());
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String css = response.body().string();
                    cssCache.put(cssFileName, css);
                    return css;
                }
            }
        } catch (IOException e) {
            log.warn("获取词典CSS失败: file={}, error={}", cssFileName, e.getMessage());
        }
        return null;
    }

    @Override
    public byte[] getAudio(String filePath) {
        return getAudio(null, filePath);
    }

    @Override
    public byte[] getAudio(String dictName, String filePath) {
        try {
            StringBuilder urlBuilder = new StringBuilder(
                    dictServiceUrl + "/audio?file=" + URLEncoder.encode(filePath, "UTF-8"));
            if (dictName != null && !dictName.isEmpty()) {
                urlBuilder.append("&dict=").append(URLEncoder.encode(dictName, "UTF-8"));
            }
            Request request = new Request.Builder().url(urlBuilder.toString()).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().bytes();
                }
            }
        } catch (IOException e) {
            log.warn("离线词典音频获取失败: file={}, error={}", filePath, e.getMessage());
        }
        return null;
    }

    @Override
    public byte[] getStaticResource(String filePath) {
        try {
            String url = dictServiceUrl + "/static/" + URLEncoder.encode(filePath, "UTF-8");
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().bytes();
                }
            }
        } catch (IOException e) {
            log.warn("离线词典静态资源获取失败: file={}, error={}", filePath, e.getMessage());
        }
        return null;
    }
}
