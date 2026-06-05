package com.geekyan.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.geekyan.entity.Word;
import com.geekyan.service.IDictionaryService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class DictionaryServiceImpl implements IDictionaryService {

    private static final Logger log = LoggerFactory.getLogger(DictionaryServiceImpl.class);

    private static final String FREE_DICT_URL = "https://api.dictionaryapi.dev/api/v2/entries/en/";
    private static final String YOUDAO_URL = "https://dict.youdao.com/jsonapi?q=";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    @Override
    public Word lookupWord(String word) {
        Word result = new Word();
        result.setWord(word);

        boolean dictOk = fillFromFreeDict(result, word);
        boolean youdaoOk = fillFromYoudao(result, word);

        if (!dictOk && !youdaoOk) {
            return null;
        }

        if (result.getFrequency() == null) {
            result.setFrequency(50);
        }
        if (result.getDifficulty() == null) {
            result.setDifficulty(guessDifficulty(word));
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> fuzzySuggest(String keyword, int limit) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        try {
            String url = YOUDAO_URL + keyword + "&doctype=json&le=en";
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JSONObject json = JSON.parseObject(body);
                    JSONObject webTrans = json.getJSONObject("web_trans");
                    if (webTrans != null) {
                        JSONArray transArr = webTrans.getJSONArray("web-translation");
                        if (transArr != null) {
                            int count = 0;
                            for (int i = 0; i < transArr.size() && count < limit; i++) {
                                JSONObject item = transArr.getJSONObject(i);
                                String key = item.getString("key");
                                if (key != null && !key.equalsIgnoreCase(keyword) && key.toLowerCase().startsWith(keyword.toLowerCase())) {
                                    Map<String, Object> wordObj = new HashMap<>();
                                    wordObj.put("word", key);
                                    wordObj.put("phonetic", "");
                                    wordObj.put("meanings", "[]");
                                    suggestions.add(wordObj);
                                    count++;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("有道模糊查询失败: {}", e.getMessage());
        }
        return suggestions;
    }

    private boolean fillFromFreeDict(Word result, String word) {
        try {
            String url = FREE_DICT_URL + word.toLowerCase().trim();
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return false;
                }
                String body = response.body().string();
                JSONArray arr = JSON.parseArray(body);
                if (arr == null || arr.isEmpty()) {
                    return false;
                }
                JSONObject data = arr.getJSONObject(0);

                if (result.getPhonetic() == null || result.getPhonetic().isEmpty()) {
                    String phonetic = data.getString("phonetic");
                    if (phonetic == null || phonetic.isEmpty()) {
                        JSONArray phonetics = data.getJSONArray("phonetics");
                        if (phonetics != null) {
                            for (int i = 0; i < phonetics.size(); i++) {
                                JSONObject p = phonetics.getJSONObject(i);
                                String text = p.getString("text");
                                if (text != null && !text.isEmpty()) {
                                    phonetic = text;
                                    break;
                                }
                            }
                        }
                    }
                    if (phonetic != null) {
                        result.setPhonetic(phonetic);
                    }
                }

                JSONArray meaningsArr = data.getJSONArray("meanings");
                if (meaningsArr != null && !meaningsArr.isEmpty()) {
                    JSONArray meaningsList = new JSONArray();
                    JSONArray examplesList = new JSONArray();

                    for (int i = 0; i < meaningsArr.size(); i++) {
                        JSONObject meaning = meaningsArr.getJSONObject(i);
                        String pos = meaning.getString("partOfSpeech");
                        JSONArray definitions = meaning.getJSONArray("definitions");

                        if (definitions != null && !definitions.isEmpty()) {
                            JSONObject meaningObj = new JSONObject();
                            meaningObj.put("pos", pos);
                            StringBuilder defBuilder = new StringBuilder();
                            for (int j = 0; j < Math.min(definitions.size(), 3); j++) {
                                JSONObject def = definitions.getJSONObject(j);
                                String definition = def.getString("definition");
                                if (definition != null) {
                                    if (defBuilder.length() > 0) defBuilder.append("; ");
                                    defBuilder.append(definition);
                                }
                                String example = def.getString("example");
                                if (example != null && examplesList.size() < 4) {
                                    JSONObject exObj = new JSONObject();
                                    exObj.put("en", example);
                                    exObj.put("cn", "");
                                    examplesList.add(exObj);
                                }
                            }
                            meaningObj.put("def", defBuilder.toString());
                            meaningsList.add(meaningObj);
                        }
                    }

                    if (result.getMeanings() == null || result.getMeanings().isEmpty()) {
                        result.setMeanings(meaningsList.toJSONString());
                    }
                    if ((result.getExamples() == null || result.getExamples().isEmpty()) && !examplesList.isEmpty()) {
                        result.setExamples(examplesList.toJSONString());
                    }
                }
                return true;
            }
        } catch (IOException e) {
            log.warn("Free Dictionary API 查询失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean fillFromYoudao(Word result, String word) {
        try {
            String url = YOUDAO_URL + word + "&doctype=json&le=en";
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return false;
                }
                String body = response.body().string();
                JSONObject json = JSON.parseObject(body);

                JSONObject ec = json.getJSONObject("ec");
                if (ec != null) {
                    JSONArray wordArr = ec.getJSONArray("word");
                    JSONObject firstWord = null;
                    if (wordArr != null && !wordArr.isEmpty()) {
                        firstWord = wordArr.getJSONObject(0);
                    }

                    if (firstWord != null) {
                        if (result.getPhonetic() == null || result.getPhonetic().isEmpty()) {
                            String uk = firstWord.getString("ukphone");
                            String us = firstWord.getString("usphone");
                            if (uk != null && !uk.isEmpty() && us != null && !us.isEmpty()) {
                                result.setPhonetic("英 /" + uk + "/ 美 /" + us + "/");
                            } else if (uk != null && !uk.isEmpty()) {
                                result.setPhonetic("/" + uk + "/");
                            } else if (us != null && !us.isEmpty()) {
                                result.setPhonetic("/" + us + "/");
                            }
                        }

                        JSONArray cnMeanings = new JSONArray();
                        JSONArray trs = firstWord.getJSONArray("trs");
                        if (trs != null) {
                            for (int i = 0; i < Math.min(trs.size(), 5); i++) {
                                JSONObject trItem = trs.getJSONObject(i);
                                JSONArray trArr = trItem.getJSONArray("tr");
                                if (trArr != null && !trArr.isEmpty()) {
                                    for (int j = 0; j < trArr.size(); j++) {
                                        JSONObject t = trArr.getJSONObject(j);
                                        JSONObject lObj = t.getJSONObject("l");
                                        if (lObj != null) {
                                            JSONArray iArr = lObj.getJSONArray("i");
                                            if (iArr != null && !iArr.isEmpty()) {
                                                String text = iArr.getString(0);
                                                if (text != null && !text.isEmpty()) {
                                                    String pos = "";
                                                    String def = text;
                                                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([\\w.]+\\.?)\\s+(.+)$").matcher(text);
                                                    if (m.find()) {
                                                        pos = m.group(1);
                                                        def = m.group(2);
                                                    }
                                                    JSONObject meaningObj = new JSONObject();
                                                    meaningObj.put("pos", pos);
                                                    meaningObj.put("def", def);
                                                    cnMeanings.add(meaningObj);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (!cnMeanings.isEmpty()) {
                            if (result.getMeanings() == null || result.getMeanings().isEmpty()) {
                                result.setMeanings(cnMeanings.toJSONString());
                            } else {
                                JSONArray existingMeanings = JSON.parseArray(result.getMeanings());
                                for (int i = 0; i < existingMeanings.size() && i < cnMeanings.size(); i++) {
                                    JSONObject existing = existingMeanings.getJSONObject(i);
                                    JSONObject cn = cnMeanings.getJSONObject(i);
                                    String cnDef = cn.getString("def");
                                    if (cnDef != null && !cnDef.isEmpty()) {
                                        existing.put("def", cnDef);
                                        String cnPos = cn.getString("pos");
                                        if (cnPos != null && !cnPos.isEmpty()) {
                                            existing.put("pos", cnPos);
                                        }
                                    }
                                }
                                result.setMeanings(existingMeanings.toJSONString());
                            }
                        }
                    }
                }

                if (result.getMeanings() == null || result.getMeanings().isEmpty()) {
                    JSONObject ee = json.getJSONObject("ee");
                    if (ee != null) {
                        JSONObject eeWord = ee.getJSONObject("word");
                        if (eeWord != null) {
                            JSONArray eeTrs = eeWord.getJSONArray("trs");
                            if (eeTrs != null) {
                                JSONArray eeMeanings = new JSONArray();
                                for (int i = 0; i < eeTrs.size(); i++) {
                                    JSONObject trItem = eeTrs.getJSONObject(i);
                                    String pos = trItem.getString("pos");
                                    JSONArray trArr = trItem.getJSONArray("tr");
                                    if (trArr != null) {
                                        for (int j = 0; j < trArr.size(); j++) {
                                            JSONObject t = trArr.getJSONObject(j);
                                            JSONObject lObj = t.getJSONObject("l");
                                            if (lObj != null) {
                                                JSONArray iArr = lObj.getJSONArray("i");
                                                if (iArr != null && !iArr.isEmpty()) {
                                                    String text = iArr.getString(0);
                                                    if (text != null) {
                                                        JSONObject m = new JSONObject();
                                                        m.put("pos", pos != null ? pos : "");
                                                        m.put("def", text);
                                                        eeMeanings.add(m);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (!eeMeanings.isEmpty()) {
                                    result.setMeanings(eeMeanings.toJSONString());
                                }
                            }
                        }
                    }
                }

                if (result.getExamples() == null || result.getExamples().isEmpty()) {
                    JSONObject blngSentsPart = json.getJSONObject("blng_sents_part");
                    if (blngSentsPart != null) {
                        JSONArray pairs = blngSentsPart.getJSONArray("sentence-pair");
                        if (pairs != null && !pairs.isEmpty()) {
                            JSONArray examplesList = new JSONArray();
                            for (int i = 0; i < Math.min(pairs.size(), 3); i++) {
                                JSONObject pair = pairs.getJSONObject(i);
                                JSONObject ex = new JSONObject();
                                ex.put("en", pair.getString("sentence"));
                                ex.put("cn", pair.getString("sentence-translation"));
                                examplesList.add(ex);
                            }
                            result.setExamples(examplesList.toJSONString());
                        }
                    }
                }

                return result.getMeanings() != null && !result.getMeanings().isEmpty();
            }
        } catch (IOException e) {
            log.warn("有道词典查询失败: {}", e.getMessage());
            return false;
        }
    }

    private String guessDifficulty(String word) {
        int len = word.length();
        if (len <= 4) return "easy";
        if (len <= 7) return "medium";
        return "hard";
    }
}
