package com.geekyan.task;

import com.geekyan.service.AnalyticsAIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 学情分析定时任务
 * 每天凌晨自动清除AI分析缓存，下次请求时重新生成
 */
@Component
public class AnalyticsScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsScheduledTask.class);

    @Autowired
    private AnalyticsAIService analyticsAIService;

    /**
     * 每天凌晨2点清除所有AI分析缓存
     * 缓存清除后，用户下次请求时会自动重新生成最新数据
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void evictAllAnalyticsCache() {
        log.info("=== 学情分析定时任务：开始清除AI分析缓存 ===");
        try {
            // 清除Redis中所有学情分析相关缓存
            // 由于evictAllCache需要userId，这里通过清除缓存key前缀来实现
            analyticsAIService.evictAllCacheByPrefix();
            log.info("=== 学情分析定时任务：AI分析缓存清除完成 ===");
        } catch (Exception e) {
            log.error("学情分析定时任务执行失败: {}", e.getMessage());
        }
    }
}
