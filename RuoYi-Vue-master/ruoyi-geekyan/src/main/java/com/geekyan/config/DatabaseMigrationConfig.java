package com.geekyan.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

@Configuration
public class DatabaseMigrationConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationConfig.class);

    @Autowired
    private DataSource dataSource;

    @PostConstruct
    public void migrate() {
        try (Connection conn = dataSource.getConnection()) {
            migrateAiNoteTable(conn);
            migrateReviewTaskTable(conn);
            migrateChatMessageTable(conn);
            createSentenceAnalysisCacheTable(conn);
            log.info("数据库迁移检查完成");
        } catch (Exception e) {
            log.error("数据库迁移失败: {}", e.getMessage());
        }
    }

    private void migrateAiNoteTable(Connection conn) throws Exception {
        Set<String> columns = getColumns(conn, "ai_note");

        if (!columns.contains("difficulty")) {
            executeAlter(conn,
                    "ALTER TABLE ai_note ADD COLUMN difficulty VARCHAR(16) DEFAULT NULL COMMENT '难度: easy/medium/hard' AFTER related_knowledge");
            log.info("ai_note表添加difficulty列");
        }
        if (!columns.contains("source_article_id")) {
            executeAlter(conn,
                    "ALTER TABLE ai_note ADD COLUMN source_article_id BIGINT DEFAULT NULL COMMENT '精读关联文章ID' AFTER source_id");
            log.info("ai_note表添加source_article_id列");
        }
    }

    private void migrateReviewTaskTable(Connection conn) throws Exception {
        Set<String> columns = getColumns(conn, "review_task");
        if (!columns.contains("accuracy_score")) {
            executeAlter(conn,
                    "ALTER TABLE review_task ADD COLUMN accuracy_score INT DEFAULT NULL COMMENT '正确率评分(0-100)' AFTER is_completed");
            log.info("review_task表添加accuracy_score列");
        }
        if (!columns.contains("mastery_level")) {
            executeAlter(conn,
                    "ALTER TABLE review_task ADD COLUMN mastery_level DOUBLE DEFAULT 0 COMMENT '掌握程度(0-1)' AFTER accuracy_score");
            log.info("review_task表添加mastery_level列");
        }
    }

    private void migrateChatMessageTable(Connection conn) throws Exception {
        Set<String> columns = getColumns(conn, "chat_message");
        if (!columns.contains("subject")) {
            executeAlter(conn,
                    "ALTER TABLE chat_message ADD COLUMN subject VARCHAR(32) DEFAULT NULL COMMENT '学科: english/math/ds/os/co/cn' AFTER send_message_id");
            log.info("chat_message表添加subject列");
        }
    }

    private Set<String> getColumns(Connection conn, String tableName) throws Exception {
        Set<String> columns = new HashSet<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, tableName, null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return columns;
    }

    private void executeAlter(Connection conn, String sql) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void createSentenceAnalysisCacheTable(Connection conn) throws Exception {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(conn.getCatalog(), null, "sentence_analysis_cache", null)) {
            if (rs.next()) {
                log.info("sentence_analysis_cache表已存在，跳过创建");
                return;
            }
        }

        String sql = "CREATE TABLE sentence_analysis_cache (" +
                "id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键'," +
                "user_id BIGINT NOT NULL COMMENT '用户ID'," +
                "sentence TEXT NOT NULL COMMENT '英文原文句子'," +
                "sentence_hash VARCHAR(32) NOT NULL COMMENT '句子MD5哈希'," +
                "full_analysis LONGTEXT COMMENT 'AI完整解析原文'," +
                "section1 TEXT COMMENT '第1段：原句重现与整体感知'," +
                "section2 TEXT COMMENT '第2段：核心词汇与地道表达'," +
                "section3 TEXT COMMENT '第3段：语法名词剖析'," +
                "section4 TEXT COMMENT '第4段：深度解析与考点提示'," +
                "translate_literal VARCHAR(512) DEFAULT NULL COMMENT '直译'," +
                "translate_free VARCHAR(512) DEFAULT NULL COMMENT '意译'," +
                "ai_model VARCHAR(32) DEFAULT NULL COMMENT 'AI模型标识'," +
                "hit_count INT DEFAULT 0 COMMENT '缓存命中次数'," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "PRIMARY KEY (id)," +
                "UNIQUE KEY uk_user_hash (user_id, sentence_hash)," +
                "KEY idx_user_id (user_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='精读句子解析缓存表'";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("sentence_analysis_cache表创建成功");
        }
    }
}
