package com.geekyan.controller;

import com.geekyan.service.IOfflineDictService;
import com.geekyan.service.QueryCacheService;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/geekyan/offline-dict")
public class OfflineDictController extends BaseController {

    @Autowired
    private IOfflineDictService offlineDictService;

    @Autowired
    private QueryCacheService queryCacheService;

    @Anonymous
    @GetMapping("/search")
    public AjaxResult search(@RequestParam String word, @RequestParam(required = false) String dict) {
        if (word == null || word.trim().isEmpty()) {
            return error("单词不能为空");
        }
        String cached = queryCacheService.getOfflineLookup(word.trim(), dict);
        if (cached != null && !cached.isEmpty()) {
            try {
                return success(JSON.parseObject(cached, Map.class));
            } catch (Exception ignored) {
            }
        }
        try {
            Map<String, Object> result = offlineDictService.lookupWord(word.trim(), dict);
            Object results = result.get("results");
            if (results != null && results instanceof Map && !((Map<?, ?>) results).isEmpty()) {
                queryCacheService.setOfflineLookup(word.trim(), dict, result);
                return success(result);
            }
            return error("未找到该单词的离线词典释义");
        } catch (Exception e) {
            return error("离线词典查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/lookup")
    public AjaxResult lookup(@RequestParam String word) {
        if (word == null || word.trim().isEmpty()) {
            return error("单词不能为空");
        }
        String cached = queryCacheService.getOfflineLookup(word.trim(), null);
        if (cached != null && !cached.isEmpty()) {
            try {
                return success(JSON.parseObject(cached, Map.class));
            } catch (Exception ignored) {
            }
        }
        try {
            Map<String, Object> result = offlineDictService.lookupWord(word.trim());
            Object results = result.get("results");
            if (results != null && results instanceof Map && !((Map<?, ?>) results).isEmpty()) {
                queryCacheService.setOfflineLookup(word.trim(), null, result);
                return success(result);
            }
            return error("未找到该单词的离线词典释义");
        } catch (Exception e) {
            return error("离线词典查询失败: " + e.getMessage());
        }
    }

    @Anonymous
    @GetMapping("/fuzzy")
    public AjaxResult fuzzy(@RequestParam String keyword, @RequestParam(defaultValue = "10") int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return error("关键词不能为空");
        }
        String cached = queryCacheService.getOfflineFuzzy(keyword.trim(), limit);
        if (cached != null && !cached.isEmpty()) {
            try {
                return success(JSON.parseArray(cached, Map.class));
            } catch (Exception ignored) {
            }
        }
        try {
            List<Map<String, Object>> suggestions = offlineDictService.fuzzySuggest(keyword.trim(), limit);
            queryCacheService.setOfflineFuzzy(keyword.trim(), limit, suggestions);
            return success(suggestions);
        } catch (Exception e) {
            return error("模糊查询失败: " + e.getMessage());
        }
    }

    @Anonymous
    @GetMapping("/reverse")
    public AjaxResult reverse(@RequestParam String q, @RequestParam(defaultValue = "20") int limit) {
        if (q == null || q.trim().isEmpty()) {
            return error("查询内容不能为空");
        }
        String cached = queryCacheService.getOfflineReverse(q.trim(), limit);
        if (cached != null && !cached.isEmpty()) {
            try {
                return success(JSON.parseObject(cached, Map.class));
            } catch (Exception ignored) {
            }
        }
        try {
            List<Map<String, Object>> results = offlineDictService.reverseSearch(q.trim(), limit);
            List<Map<String, Object>> enhanced = new ArrayList<>();
            if (results != null) {
                for (Map<String, Object> item : results) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("match", item.get("match"));
                    entry.put("word", item.get("word"));
                    entry.put("definition", item.get("definition") != null ? item.get("definition") : "");
                    entry.put("source", "offline");
                    enhanced.add(entry);
                }
            }
            Map<String, Object> data = new HashMap<>();
            data.put("query", q);
            data.put("results", enhanced);
            data.put("source", "offline");
            data.put("sourceLabel", "离线词典");
            queryCacheService.setOfflineReverse(q.trim(), limit, data);
            return success(data);
        } catch (Exception e) {
            return error("反向查询失败: " + e.getMessage());
        }
    }

    @Anonymous
    @GetMapping("/dicts")
    public AjaxResult dicts() {
        try {
            List<Map<String, Object>> dicts = offlineDictService.listDicts();
            return success(dicts);
        } catch (Exception e) {
            return error("获取词典列表失败: " + e.getMessage());
        }
    }

    @Anonymous
    @GetMapping("/audio")
    public ResponseEntity<byte[]> audio(@RequestParam String file) {
        if (file == null || file.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            byte[] data = offlineDictService.getAudio(file.trim());
            if (data != null && data.length > 0) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
                headers.setContentLength(data.length);
                headers.setCacheControl("max-age=86400");
                return new ResponseEntity<>(data, headers, HttpStatus.OK);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Anonymous
    @GetMapping("/static/{filename:.+}")
    public ResponseEntity<byte[]> staticResource(@PathVariable String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            byte[] data = offlineDictService.getStaticResource(filename.trim());
            if (data != null && data.length > 0) {
                HttpHeaders headers = new HttpHeaders();
                String contentType = guessContentType(filename);
                headers.setContentType(MediaType.parseMediaType(contentType));
                headers.setContentLength(data.length);
                headers.setCacheControl("max-age=86400");
                return new ResponseEntity<>(data, headers, HttpStatus.OK);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String guessContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".css"))
            return "text/css";
        if (lower.endsWith(".js"))
            return "application/javascript";
        if (lower.endsWith(".png"))
            return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return "image/jpeg";
        if (lower.endsWith(".gif"))
            return "image/gif";
        if (lower.endsWith(".svg"))
            return "image/svg+xml";
        if (lower.endsWith(".woff") || lower.endsWith(".woff2"))
            return "font/woff";
        if (lower.endsWith(".ttf"))
            return "font/ttf";
        return "application/octet-stream";
    }
}
