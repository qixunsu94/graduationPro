package com.geekyan.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.redis.RedisCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class QueryCacheService {

  private static final Logger log = LoggerFactory.getLogger(QueryCacheService.class);

  private static final String KEY_PREFIX = "geekyan:query:";

  private static final int TTL_WORD_SEARCH = 24;
  private static final int TTL_REVERSE = 12;
  private static final int TTL_PHRASE = 24;
  private static final int TTL_MNEMONIC = 72;
  private static final int TTL_TRANSLATE = 7;
  private static final int TTL_OFFLINE = 48;
  private static final int TTL_MSG_TRANSLATE = 24;
  private static final int TTL_AI_ANALYSIS = 3;
  private static final int TTL_SENTENCE_ANALYSIS = 7;

  @Autowired
  private RedisCache redisCache;

  public String getWordSearch(String word) {
    return get(KEY_PREFIX + "word:" + word.toLowerCase());
  }

  public void setWordSearch(String word, Object data) {
    set(KEY_PREFIX + "word:" + word.toLowerCase(), data, TTL_WORD_SEARCH, TimeUnit.HOURS);
  }

  public String getReverse(String query, int limit) {
    return get(KEY_PREFIX + "reverse:" + query + ":" + limit);
  }

  public void setReverse(String query, int limit, Object data) {
    set(KEY_PREFIX + "reverse:" + query + ":" + limit, data, TTL_REVERSE, TimeUnit.HOURS);
  }

  public String getTranslatePhrase(String text, String to) {
    return get(KEY_PREFIX + "phrase:" + text.toLowerCase() + ":" + to);
  }

  public void setTranslatePhrase(String text, String to, Object data) {
    set(KEY_PREFIX + "phrase:" + text.toLowerCase() + ":" + to, data, TTL_PHRASE, TimeUnit.HOURS);
  }

  public String getMnemonic(String word) {
    return get(KEY_PREFIX + "mnemonic:" + word.toLowerCase());
  }

  public void setMnemonic(String word, Object data) {
    set(KEY_PREFIX + "mnemonic:" + word.toLowerCase(), data, TTL_MNEMONIC, TimeUnit.HOURS);
  }

  /** 安全缓存key：用原始字符串替代hashCode避免冲突，超长截断 */
  private String safeKey(String text) {
    if (text == null)
      return "null";
    String s = text.trim().toLowerCase();
    return s.length() > 200 ? s.substring(0, 200) + ":h" + s.hashCode() : s;
  }

  public String getTranslate(String text, String from, String to) {
    return get(KEY_PREFIX + "translate:" + safeKey(text) + ":" + from + ":" + to);
  }

  public void setTranslate(String text, String from, String to, Object data) {
    set(KEY_PREFIX + "translate:" + safeKey(text) + ":" + from + ":" + to, data, TTL_TRANSLATE, TimeUnit.DAYS);
  }

  public void evictTranslate(String text, String from, String to) {
    delete(KEY_PREFIX + "translate:" + safeKey(text) + ":" + from + ":" + to);
  }

  public String getOfflineLookup(String word, String dict) {
    return get(KEY_PREFIX + "offline:" + word.toLowerCase() + ":" + (dict != null ? dict : ""));
  }

  public void setOfflineLookup(String word, String dict, Object data) {
    set(KEY_PREFIX + "offline:" + word.toLowerCase() + ":" + (dict != null ? dict : ""), data, TTL_OFFLINE,
        TimeUnit.HOURS);
  }

  public String getOfflineReverse(String query, int limit) {
    return get(KEY_PREFIX + "offline-reverse:" + query + ":" + limit);
  }

  public void setOfflineReverse(String query, int limit, Object data) {
    set(KEY_PREFIX + "offline-reverse:" + query + ":" + limit, data, TTL_OFFLINE, TimeUnit.HOURS);
  }

  public String getOfflineFuzzy(String keyword, int limit) {
    return get(KEY_PREFIX + "offline-fuzzy:" + keyword.toLowerCase() + ":" + limit);
  }

  public void setOfflineFuzzy(String keyword, int limit, Object data) {
    set(KEY_PREFIX + "offline-fuzzy:" + keyword.toLowerCase() + ":" + limit, data, TTL_OFFLINE, TimeUnit.HOURS);
  }

  public String getMsgTranslate(String messageId) {
    return get(KEY_PREFIX + "msg-translate:" + messageId);
  }

  public void setMsgTranslate(String messageId, Object data) {
    set(KEY_PREFIX + "msg-translate:" + messageId, data, TTL_MSG_TRANSLATE, TimeUnit.HOURS);
  }

  public void evictWordSearch(String word) {
    delete(KEY_PREFIX + "word:" + word.toLowerCase());
  }

  public void evictTranslatePhrase(String text, String to) {
    delete(KEY_PREFIX + "phrase:" + text.toLowerCase() + ":" + to);
  }

  public String getAIAnalysis(Long userId) {
    return get(KEY_PREFIX + "ai-analysis:" + userId);
  }

  public void setAIAnalysis(Long userId, Object data) {
    set(KEY_PREFIX + "ai-analysis:" + userId, data, TTL_AI_ANALYSIS, TimeUnit.HOURS);
  }

  public void evictAIAnalysis(Long userId) {
    delete(KEY_PREFIX + "ai-analysis:" + userId);
  }

  public void evictAllWordSearch() {
    try {
      Collection<String> keys = redisCache.keys(KEY_PREFIX + "word:*");
      if (keys != null && !keys.isEmpty()) {
        for (String key : keys) {
          redisCache.deleteObject(key);
        }
      }
    } catch (Exception e) {
      log.warn("批量清除单词查询缓存失败: error={}", e.getMessage());
    }
  }

  public String getSentenceAnalysis(String key) {
    return get(KEY_PREFIX + "sentence-analysis:" + safeKey(key));
  }

  public void setSentenceAnalysis(String key, Object data) {
    set(KEY_PREFIX + "sentence-analysis:" + safeKey(key), data, TTL_SENTENCE_ANALYSIS, TimeUnit.DAYS);
  }

  /** 中文词→英文查询缓存（含反向搜索+翻译+AI分析，TTL 24h） */
  public String getZhWordQuery(String query) {
    return get(KEY_PREFIX + "zh-word:" + safeKey(query));
  }

  public void setZhWordQuery(String query, Object data) {
    set(KEY_PREFIX + "zh-word:" + safeKey(query), data, TTL_WORD_SEARCH, TimeUnit.HOURS);
  }

  /** 统一查询缓存（所有queryType共用，TTL 24h） */
  public String getUnifiedQuery(String query) {
    return get(KEY_PREFIX + "unified:" + safeKey(query));
  }

  public void setUnifiedQuery(String query, Object data) {
    set(KEY_PREFIX + "unified:" + safeKey(query), data, TTL_WORD_SEARCH, TimeUnit.HOURS);
  }

  /** 通用缓存读取（公开） */
  public String get(String key) {
    try {
      return redisCache.getCacheObject(key);
    } catch (Exception e) {
      log.warn("Redis缓存读取失败: key={}, error={}", key, e.getMessage());
      return null;
    }
  }

  /** 通用缓存写入（公开，秒为单位） */
  public void set(String key, Object data, long timeoutSeconds) {
    try {
      String json = data instanceof String ? (String) data : JSON.toJSONString(data);
      redisCache.setCacheObject(key, json, (int) timeoutSeconds, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.warn("Redis缓存写入失败: key={}, error={}", key, e.getMessage());
    }
  }

  private void set(String key, Object data, long timeout, TimeUnit unit) {
    try {
      String json = data instanceof String ? (String) data : JSON.toJSONString(data);
      redisCache.setCacheObject(key, json, (int) timeout, unit);
    } catch (Exception e) {
      log.warn("Redis缓存写入失败: key={}, error={}", key, e.getMessage());
    }
  }

  private void delete(String key) {
    try {
      redisCache.deleteObject(key);
    } catch (Exception e) {
      log.warn("Redis缓存删除失败: key={}, error={}", key, e.getMessage());
    }
  }
}
