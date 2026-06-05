package com.geekyan.service.impl;

import com.geekyan.service.IBaiduTranslateService;
import com.geekyan.service.QueryCacheService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

@Service
public class BaiduTranslateServiceImpl implements IBaiduTranslateService {

    private static final Logger log = LoggerFactory.getLogger(BaiduTranslateServiceImpl.class);

    @Value("${geekyan.baidu-translate.appid:}")
    private String appId;

    @Value("${geekyan.baidu-translate.secret:}")
    private String secret;

    @Autowired
    private QueryCacheService queryCacheService;

    private static final String API_URL = "https://fanyi-api.baidu.com/api/trans/vip/translate";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    @Override
    public String translate(String query, String from, String to) {
        // 优先从缓存获取
        String cached = queryCacheService.getTranslate(query, from, to);
        if (cached != null) {
            if (isUsableTranslation(query, cached, from, to)) {
                log.debug("百度翻译缓存命中: query={}", query);
                return cached;
            }
            queryCacheService.evictTranslate(query, from, to);
            log.warn("百度翻译缓存无效，已忽略: query={}, from={}, to={}, cached={}", query, from, to, cached);
        }

        if (appId == null || appId.isEmpty() || secret == null || secret.isEmpty()) {
            log.warn("百度翻译API未配置，使用AI翻译回退");
            return null;
        }

        String salt = String.valueOf(System.currentTimeMillis());
        String sign = md5(appId + query + salt + secret);

        String url = API_URL + "?q=" + encode(query) +
                "&from=" + from +
                "&to=" + to +
                "&appid=" + appId +
                "&salt=" + salt +
                "&sign=" + sign;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(body);

                if (json.containsKey("error_code")) {
                    log.error("百度翻译API错误: {} - {}", json.getString("error_code"), json.getString("error_msg"));
                    return null;
                }

                com.alibaba.fastjson2.JSONArray transResult = json.getJSONArray("trans_result");
                if (transResult != null && !transResult.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < transResult.size(); i++) {
                        com.alibaba.fastjson2.JSONObject item = transResult.getJSONObject(i);
                        if (i > 0)
                            sb.append("\n");
                        sb.append(item.getString("dst"));
                    }
                    String result = sb.toString();
                    if (isUsableTranslation(query, result, from, to)) {
                        queryCacheService.setTranslate(query, from, to, result);
                        return result;
                    }
                    log.warn("百度翻译结果无效，触发上层回退: query={}, from={}, to={}, result={}", query, from, to, result);
                    return null;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("百度翻译请求失败: {}", e.getMessage());
            return null;
        }
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 error", e);
        }
    }

    private String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private boolean isUsableTranslation(String query, String result, String from, String to) {
        if (result == null || result.trim().isEmpty()) {
            return false;
        }
        String src = normalize(query);
        String dst = normalize(result);
        if (!src.isEmpty() && src.equalsIgnoreCase(dst) && isCrossLanguage(from, to)) {
            return false;
        }
        if ("en".equalsIgnoreCase(to) && containsChinese(result)) {
            return false;
        }
        if ("zh".equalsIgnoreCase(to) && isMostlyEnglish(result) && containsChinese(query)) {
            return false;
        }
        return true;
    }

    private boolean isCrossLanguage(String from, String to) {
        return from != null && to != null && !from.equalsIgnoreCase(to) && !"auto".equalsIgnoreCase(to);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private boolean containsChinese(String value) {
        return value != null && value.codePoints().anyMatch(c -> c >= 0x4e00 && c <= 0x9fff);
    }

    private boolean isMostlyEnglish(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        long latin = value.codePoints().filter(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')).count();
        long chinese = value.codePoints().filter(c -> c >= 0x4e00 && c <= 0x9fff).count();
        return latin > 0 && latin >= chinese;
    }
}
