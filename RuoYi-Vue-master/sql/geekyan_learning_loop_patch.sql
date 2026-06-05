USE geekyan;

-- AI practice / Ebbinghaus review separation patch.
-- Run this after geekyan.sql on existing databases.

ALTER TABLE review_task
  ADD COLUMN IF NOT EXISTS answer_content TEXT DEFAULT NULL COMMENT '复习答案/背面内容' AFTER content;

ALTER TABLE review_task
  MODIFY COLUMN related_type VARCHAR(32) NOT NULL COMMENT '关联类型: word/note/sentence/reading_note/reading_collect';

ALTER TABLE review_task
  ADD UNIQUE KEY uk_review_source (user_id, related_type, related_id);

CREATE TABLE IF NOT EXISTS word_book_category (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  name VARCHAR(64) NOT NULL COMMENT '词本分类名称',
  is_default TINYINT DEFAULT 0 COMMENT '是否默认分类: 0否 1是',
  sort_order INT DEFAULT 0 COMMENT '排序',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_category_name (user_id, name),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生词本分类表';

ALTER TABLE word_book
  ADD COLUMN IF NOT EXISTS book_id BIGINT DEFAULT NULL COMMENT '词本分类ID' AFTER book_name;

INSERT IGNORE INTO word_book_category (user_id, name, is_default, sort_order)
SELECT DISTINCT
  user_id,
  COALESCE(NULLIF(book_name, ''), '默认生词本') AS name,
  CASE WHEN book_name IS NULL OR book_name = '' OR book_name = '默认生词本' THEN 1 ELSE 0 END AS is_default,
  0 AS sort_order
FROM word_book
WHERE del_flag = 0;

UPDATE word_book wb
JOIN word_book_category c
  ON c.user_id = wb.user_id
 AND c.name = COALESCE(NULLIF(wb.book_name, ''), '默认生词本')
SET wb.book_id = c.id
WHERE wb.book_id IS NULL;

CREATE TABLE IF NOT EXISTS reading_session (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  document_id BIGINT DEFAULT NULL COMMENT '精读文档ID',
  source_file VARCHAR(256) DEFAULT NULL COMMENT '来源文件名',
  start_time DATETIME NOT NULL COMMENT '开始时间',
  end_time DATETIME DEFAULT NULL COMMENT '结束时间',
  duration_seconds INT DEFAULT 0 COMMENT '总阅读秒数',
  active_seconds INT DEFAULT 0 COMMENT '有效阅读秒数',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, start_time),
  KEY idx_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='精读阅读时段记录表';

ALTER TABLE learning_record
  ADD COLUMN IF NOT EXISTS subject VARCHAR(32) DEFAULT NULL COMMENT '学科' AFTER record_type,
  ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) DEFAULT NULL COMMENT '来源类型: practice/review/reading/chat' AFTER subject,
  ADD COLUMN IF NOT EXISTS source_id VARCHAR(64) DEFAULT NULL COMMENT '来源ID' AFTER source_type;

ALTER TABLE ai_note
  MODIFY COLUMN source_type VARCHAR(32) DEFAULT NULL COMMENT '来源类型: chat/practice/daily_summary/reading/long_sentence/manual';
