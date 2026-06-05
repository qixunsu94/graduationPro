-- =============================================
-- 极研AI (GeekYan) - 智能学习助手 完整数据库脚本
-- 技术栈: MySQL 8.0 + Spring Boot + MyBatis-Plus + 若依框架
-- 使用方法: 先执行 ry_20250417.sql 和 quartz.sql 初始化若依基础表，再执行本脚本
-- =============================================

CREATE DATABASE IF NOT EXISTS geekyan DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE geekyan;

-- =============================================
-- 1. 聊天会话表 (已合并增量DDL: subject, group_id, group_ended)
-- =============================================

CREATE TABLE chat_session (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  name VARCHAR(128) DEFAULT '日常对话' COMMENT '会话名称',
  role VARCHAR(64) DEFAULT '英语老师' COMMENT 'AI角色',
  topic VARCHAR(128) DEFAULT '日常对话' COMMENT '话题',
  session_type VARCHAR(32) DEFAULT 'TOPIC' COMMENT '会话类型',
  subject VARCHAR(32) DEFAULT 'general' COMMENT '学科模式: math/ds/co/os/cn/english',
  group_id VARCHAR(64) DEFAULT NULL COMMENT '当前问题分组ID',
  group_ended TINYINT DEFAULT 0 COMMENT '当前分组是否已结束: 0否 1是',
  message_count INT DEFAULT 0 COMMENT '消息数量',
  last_message_time DATETIME DEFAULT NULL COMMENT '最后消息时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_id (session_id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

-- =============================================
-- 2. 聊天消息表 (已合并增量DDL: group_id, image_url, sections, hidden_json, subject)
-- =============================================

CREATE TABLE chat_message (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  message_id VARCHAR(64) NOT NULL COMMENT '消息ID',
  session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
  group_id VARCHAR(64) DEFAULT NULL COMMENT '问题分组ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  content TEXT COMMENT '消息内容',
  role VARCHAR(16) NOT NULL COMMENT '角色: USER/ASSISTANT',
  file_name VARCHAR(256) DEFAULT NULL COMMENT '语音文件名',
  image_url VARCHAR(512) DEFAULT NULL COMMENT '上传图片URL',
  grammar_analysis TEXT DEFAULT NULL COMMENT '语法分析',
  translation TEXT DEFAULT NULL COMMENT '翻译',
  sections JSON DEFAULT NULL COMMENT 'AI回答分段结构(JSON)',
  hidden_json TEXT DEFAULT NULL COMMENT 'AI隐藏JSON数据(包含query_type等结构化信息)',
  pronunciation_score INT DEFAULT NULL COMMENT '发音评分',
  send_message_id VARCHAR(64) DEFAULT NULL COMMENT '关联的用户消息ID',
  subject VARCHAR(32) DEFAULT NULL COMMENT '消息学科',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_message_id (message_id),
  KEY idx_session_id (session_id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- =============================================
-- 3. 单词表 (已合并增量DDL: etymology, synonyms, antonyms, word_forms, exam_tags, frequency_level)
-- =============================================

CREATE TABLE word (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  word VARCHAR(128) NOT NULL COMMENT '单词',
  phonetic VARCHAR(128) DEFAULT '' COMMENT '音标',
  meanings JSON DEFAULT NULL COMMENT '释义列表(JSON)',
  examples JSON DEFAULT NULL COMMENT '例句列表(JSON)',
  frequency INT DEFAULT 0 COMMENT '词频',
  difficulty VARCHAR(16) DEFAULT 'medium' COMMENT '难度: easy/medium/hard',
  etymology TEXT DEFAULT NULL COMMENT '词源',
  synonyms TEXT DEFAULT NULL COMMENT '同义词(JSON数组)',
  antonyms TEXT DEFAULT NULL COMMENT '反义词(JSON数组)',
  word_forms TEXT DEFAULT NULL COMMENT '词形变化(JSON)',
  exam_tags VARCHAR(512) DEFAULT NULL COMMENT '考试标签(逗号分隔)',
  frequency_level VARCHAR(32) DEFAULT NULL COMMENT '词频等级',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_word (word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词表';

-- =============================================
-- 4. 单词本表
-- =============================================

CREATE TABLE word_book (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  word_id BIGINT NOT NULL COMMENT '单词ID',
  word VARCHAR(128) NOT NULL COMMENT '单词(冗余)',
  book_name VARCHAR(64) DEFAULT '默认生词本' COMMENT '生词本名称',
  book_id BIGINT DEFAULT NULL COMMENT '词本分类ID',
  mastery_level TINYINT DEFAULT 0 COMMENT '掌握程度: 0新词 1认识 2熟悉 3掌握',
  review_count INT DEFAULT 0 COMMENT '复习次数',
  last_review_time DATETIME DEFAULT NULL COMMENT '最后复习时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_word_id (word_id),
  KEY idx_book_id (book_id),
  UNIQUE KEY uk_user_word (user_id, word_id, book_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词本表';

CREATE TABLE word_book_category (
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

-- =============================================
-- 5. 错题记录表
-- =============================================

CREATE TABLE error_record (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  subject VARCHAR(32) DEFAULT 'english' COMMENT '科目: english/math/cs/ds/co/os/cn',
  question TEXT NOT NULL COMMENT '题目内容',
  user_answer TEXT DEFAULT NULL COMMENT '用户答案',
  correct_answer TEXT DEFAULT NULL COMMENT '正确答案',
  analysis TEXT DEFAULT NULL COMMENT '解析',
  knowledge_points JSON DEFAULT NULL COMMENT '知识点标签(JSON数组)',
  learn_card JSON DEFAULT NULL COMMENT '学习卡片(JSON)',
  source VARCHAR(64) DEFAULT 'chat' COMMENT '来源: chat/practice/review',
  source_id VARCHAR(64) DEFAULT NULL COMMENT '来源ID',
  mastery_level TINYINT DEFAULT 0 COMMENT '掌握程度: 0未掌握 1部分掌握 2已掌握',
  review_count INT DEFAULT 0 COMMENT '复习次数',
  next_review_time DATETIME DEFAULT NULL COMMENT '下次复习时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_subject (subject),
  KEY idx_next_review (next_review_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题记录表';

-- =============================================
-- 6. 艾宾浩斯复习任务表
-- =============================================

CREATE TABLE review_task (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  related_type VARCHAR(32) NOT NULL COMMENT '关联类型: word/note/sentence/reading_note/reading_collect',
  related_id BIGINT NOT NULL COMMENT '关联ID(word_book.id/ai_note.id/long_sentence.id)',
  content TEXT NOT NULL COMMENT '复习内容摘要(正面展示)',
  answer_content TEXT DEFAULT NULL COMMENT '复习答案/背面内容',
  subject VARCHAR(32) DEFAULT NULL COMMENT '学科分类: math/ds/co/os/cn/english',
  review_stage INT DEFAULT 1 COMMENT '复习阶段(1-7,对应艾宾浩斯曲线)',
  review_count INT DEFAULT 0 COMMENT '已复习次数',
  next_review_time DATETIME NOT NULL COMMENT '下次复习时间',
  last_review_time DATETIME DEFAULT NULL COMMENT '上次复习时间',
  is_completed TINYINT DEFAULT 0 COMMENT '是否已完成: 0否 1是',
  accuracy_score INT DEFAULT NULL COMMENT '正确率评分(0-100)',
  mastery_level DOUBLE DEFAULT 0 COMMENT '掌握程度(0-1)',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_next_review (user_id, next_review_time, is_completed),
  KEY idx_subject (user_id, subject),
  UNIQUE KEY uk_review_source (user_id, related_type, related_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='艾宾浩斯复习任务表';

-- =============================================
-- 7. 学习记录表
-- =============================================

CREATE TABLE learning_record (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  record_type VARCHAR(32) NOT NULL COMMENT '类型: chat/word_search/practice/review',
  subject VARCHAR(32) DEFAULT NULL COMMENT '学科',
  source_type VARCHAR(32) DEFAULT NULL COMMENT '来源类型: practice/review/reading/chat',
  source_id VARCHAR(64) DEFAULT NULL COMMENT '来源ID',
  duration INT DEFAULT 0 COMMENT '学习时长(秒)',
  content_summary VARCHAR(512) DEFAULT '' COMMENT '内容摘要',
  score INT DEFAULT NULL COMMENT '得分',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_type (record_type),
  KEY idx_source (user_id, source_type, source_id),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习记录表';

CREATE TABLE reading_session (
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

-- =============================================
-- 8. PDF文档表 (已合并增量DDL: file_type)
-- =============================================

CREATE TABLE pdf_document (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  file_name VARCHAR(256) NOT NULL COMMENT '文件名',
  file_path VARCHAR(512) NOT NULL COMMENT '文件路径',
  file_size BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
  file_type VARCHAR(20) DEFAULT 'txt' COMMENT '文件类型: txt/pdf',
  page_count INT DEFAULT 0 COMMENT '页数',
  parsed_content LONGTEXT DEFAULT NULL COMMENT '解析后的文本内容',
  status TINYINT DEFAULT 0 COMMENT '状态: 0待解析 1解析中 2已完成 3失败',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PDF文档表';

-- =============================================
-- 9. 翻译缓存表
-- =============================================

CREATE TABLE translation_cache (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  document_id BIGINT NOT NULL COMMENT '文档ID',
  paragraph_index INT NOT NULL DEFAULT 0 COMMENT '段落索引',
  source_text TEXT NOT NULL COMMENT '原文',
  translated_text TEXT COMMENT '译文',
  source_lang VARCHAR(10) DEFAULT 'en' COMMENT '源语言',
  target_lang VARCHAR(10) DEFAULT 'zh' COMMENT '目标语言',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_doc_para (user_id, document_id, paragraph_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译缓存表';

-- =============================================
-- 10. 长难句表 (已合并增量DDL: literal_translation, free_translation, sentence_type)
-- =============================================

CREATE TABLE long_sentence (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  sentence TEXT NOT NULL COMMENT '英文句子',
  translation TEXT DEFAULT NULL COMMENT '中文翻译',
  literal_translation TEXT DEFAULT NULL COMMENT '直译',
  free_translation TEXT DEFAULT NULL COMMENT '意译',
  analysis TEXT DEFAULT NULL COMMENT '语法分析',
  source VARCHAR(128) DEFAULT '' COMMENT '来源',
  sentence_type VARCHAR(32) DEFAULT NULL COMMENT '类型: analysis(解析)/collection(收藏)',
  difficulty VARCHAR(16) DEFAULT 'medium' COMMENT '难度',
  grammar_tags TEXT DEFAULT NULL COMMENT '语法标签(JSON数组)',
  core_vocab TEXT DEFAULT NULL COMMENT '核心词汇(JSON数组)',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='长难句表';

-- =============================================
-- 11. 话题表
-- =============================================

CREATE TABLE topic (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  name VARCHAR(128) NOT NULL COMMENT '话题名称',
  description VARCHAR(512) DEFAULT '' COMMENT '描述',
  icon VARCHAR(64) DEFAULT '' COMMENT '图标',
  image_url VARCHAR(512) DEFAULT '' COMMENT '图片URL',
  category VARCHAR(64) DEFAULT '' COMMENT '分类',
  level INT DEFAULT 1 COMMENT '难度等级',
  sort INT DEFAULT 0 COMMENT '排序',
  status TINYINT DEFAULT 1 COMMENT '状态: 0禁用 1正常',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='话题表';

-- =============================================
-- 12. 用户设置表
-- =============================================

CREATE TABLE user_settings (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  auto_playing_voice TINYINT DEFAULT 0 COMMENT '自动播放语音: 0否 1是',
  auto_text_shadow TINYINT DEFAULT 0 COMMENT '自动文本阴影: 0否 1是',
  auto_pronunciation TINYINT DEFAULT 0 COMMENT '自动发音: 0否 1是',
  playing_voice_speed VARCHAR(8) DEFAULT '1.0' COMMENT '语音播放速度',
  speech_role_name VARCHAR(64) DEFAULT '' COMMENT '语音角色名称',
  speech_role_name_label VARCHAR(64) DEFAULT '' COMMENT '语音角色显示名',
  target_language VARCHAR(32) DEFAULT '英语' COMMENT '目标语言',
  ai_provider VARCHAR(32) DEFAULT 'ZHIPU' COMMENT 'AI提供商: ZHIPU/DEEPSEEK',
  target_year INT DEFAULT NULL COMMENT '目标考研年份',
  target_school VARCHAR(128) DEFAULT NULL COMMENT '目标院校',
  target_major VARCHAR(128) DEFAULT NULL COMMENT '目标专业',
  target_score INT DEFAULT NULL COMMENT '目标分数',
  exam_subjects VARCHAR(512) DEFAULT NULL COMMENT '考试科目(JSON数组)',
  auto_add_word TINYINT DEFAULT 1 COMMENT '查词时自动加入生词本: 0否 1是',
  default_dict VARCHAR(128) DEFAULT 'LDOCE 5 (English-Chinese, with audio)' COMMENT '默认查词词典',
  daily_word_goal INT DEFAULT 20 COMMENT '每日单词目标数',
  daily_question_goal INT DEFAULT 10 COMMENT '每日答题目标数',
  reading_mode VARCHAR(32) DEFAULT 'bilingual' COMMENT '精读模式: bilingual/english_only',
  push_enabled TINYINT DEFAULT 1 COMMENT '学习提醒推送: 0否 1是',
  push_time VARCHAR(8) DEFAULT '20:00' COMMENT '推送提醒时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设置表';

-- =============================================
-- 13. 反馈表
-- =============================================

CREATE TABLE feedback (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  content TEXT NOT NULL COMMENT '反馈内容',
  contact VARCHAR(128) DEFAULT '' COMMENT '联系方式',
  status TINYINT DEFAULT 0 COMMENT '状态: 0待处理 1已处理 2已关闭',
  reply TEXT DEFAULT NULL COMMENT '回复内容',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='反馈表';

-- =============================================
-- 14. 用户收藏表
-- =============================================

CREATE TABLE user_collect (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  type VARCHAR(32) NOT NULL COMMENT '收藏类型: message/word/sentence',
  content TEXT NOT NULL COMMENT '收藏内容',
  translation TEXT DEFAULT NULL COMMENT '翻译',
  message_id VARCHAR(64) DEFAULT NULL COMMENT '关联消息ID',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏表';

-- =============================================
-- 15. 阅读笔记表
-- =============================================

CREATE TABLE reading_note (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  document_id BIGINT DEFAULT NULL COMMENT '关联文档ID',
  paragraph_index INT DEFAULT NULL COMMENT '段落索引',
  sentence TEXT DEFAULT NULL COMMENT '关联句子',
  content TEXT NOT NULL COMMENT '笔记内容',
  source_file VARCHAR(256) DEFAULT '' COMMENT '来源文件名',
  note_type VARCHAR(32) DEFAULT 'reading' COMMENT '笔记类型: reading/analysis/translation/highlight',
  highlight_color VARCHAR(20) DEFAULT NULL COMMENT '马克笔高亮颜色key',
  selection_text TEXT DEFAULT NULL COMMENT '选中文本',
  selection_start INT DEFAULT NULL COMMENT '选区起始位置',
  selection_end INT DEFAULT NULL COMMENT '选区结束位置',
  knowledge_points VARCHAR(512) DEFAULT NULL COMMENT '关联知识点标签，逗号分隔',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_document_id (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阅读笔记表';

-- =============================================
-- 16. 搜索历史表
-- =============================================

CREATE TABLE search_history (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  keyword VARCHAR(256) NOT NULL COMMENT '搜索关键词',
  query_type VARCHAR(32) DEFAULT 'EN_WORD_TO_ZH' COMMENT '查询类型: EN_WORD_TO_ZH/EN_PHRASE_TO_ZH/EN_SENTENCE_TO_ZH/ZH_WORD_TO_EN/ZH_PHRASE_TO_EN/ZH_SENTENCE_TO_EN',
  result_word VARCHAR(256) DEFAULT '' COMMENT '结果单词',
  result_phonetic VARCHAR(256) DEFAULT '' COMMENT '结果音标',
  result_meaning VARCHAR(512) DEFAULT '' COMMENT '结果释义摘要',
  result_example VARCHAR(512) DEFAULT NULL COMMENT '结果例句',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_user_keyword (user_id, keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史表';

-- =============================================
-- 17. AI笔记/记录模块表
-- =============================================

CREATE TABLE ai_note (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  session_id VARCHAR(64) DEFAULT NULL COMMENT '关联会话ID',
  group_id VARCHAR(64) DEFAULT NULL COMMENT '问题分组ID',
  subject VARCHAR(32) DEFAULT 'general' COMMENT '学科: math/ds/co/os/cn/english',
  title VARCHAR(256) DEFAULT NULL COMMENT 'AI自动生成的标题(问题摘要)',
  question_text TEXT DEFAULT NULL COMMENT '规范化后的题目文本',
  question_image VARCHAR(512) DEFAULT NULL COMMENT '规范化后的题目图片URL',
  knowledge_tags JSON DEFAULT NULL COMMENT 'AI自动提取的知识点标签(JSON数组)',
  key_points TEXT DEFAULT NULL COMMENT '解题要点/语法要点(AI自动提取)',
  trap_types VARCHAR(512) DEFAULT NULL COMMENT '易错类型(逗号分隔)',
  related_knowledge VARCHAR(512) DEFAULT NULL COMMENT '跨知识点归因(逗号分隔)',
  difficulty VARCHAR(16) DEFAULT NULL COMMENT '难度: easy/medium/hard',
  core_vocab TEXT DEFAULT NULL COMMENT '核心词汇(JSON数组)',
  grammar_points TEXT DEFAULT NULL COMMENT '语法点(JSON数组)',
  ai_content LONGTEXT DEFAULT NULL COMMENT 'AI完整回答内容',
  user_notes TEXT DEFAULT NULL COMMENT '用户追加备注',
  follow_up_summary TEXT DEFAULT NULL COMMENT '追问记录摘要(AI归纳)',
  note_type VARCHAR(32) DEFAULT 'qa' COMMENT '笔记类型: qa/daily_summary/practice_report',
  source_type VARCHAR(32) DEFAULT NULL COMMENT '来源类型: chat/practice/daily_summary/reading/long_sentence/manual',
  source_id BIGINT DEFAULT NULL COMMENT '来源记录ID',
  source_article_id BIGINT DEFAULT NULL COMMENT '精读关联文章ID',
  is_auto_extracted TINYINT DEFAULT 1 COMMENT '是否AI自动提取: 0手动 1自动',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_subject (subject),
  KEY idx_group_id (group_id),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI记录本表';

-- =============================================
-- 初始化数据: 话题
-- =============================================

INSERT INTO topic (name, description, icon, category, level, sort) VALUES
('日常对话', '练习日常生活中的英语对话', 'chat', '生活', 1, 1),
('商务英语', '练习商务场景中的英语表达', 'briefcase', '商务', 2, 2),
('旅游英语', '练习旅游相关的英语对话', 'map', '旅游', 1, 3),
('学术英语', '学术写作和讨论的英语表达', 'reading', '学术', 3, 4),
('面试英语', '求职面试场景的英语对话', 'suitcase', '职场', 2, 5),
('医疗英语', '医疗健康相关的英语表达', 'heart', '医疗', 3, 6);

-- =============================================
-- 初始化数据: 常用英语单词
-- =============================================

INSERT INTO word (word, phonetic, meanings, examples, frequency, difficulty) VALUES
('abandon', '/əˈbændən/', '[{"pos":"vt.","def":"放弃；遗弃"},{"pos":"n.","def":"放纵"}]', '[{"en":"He abandoned his car in the snow.","cn":"他把车丢弃在雪地里。"},{"en":"She danced with wild abandon.","cn":"她纵情地跳舞。"}]', 3000, 'medium'),
('abstract', '/ˈæbstrækt/', '[{"pos":"adj.","def":"抽象的"},{"pos":"n.","def":"摘要"},{"pos":"vt.","def":"提取"}]', '[{"en":"The concept is too abstract for children.","cn":"这个概念对孩子来说太抽象了。"}]', 2500, 'medium'),
('accommodate', '/əˈkɒmədeɪt/', '[{"pos":"vt.","def":"容纳；适应；提供住宿"}]', '[{"en":"The hotel can accommodate 500 guests.","cn":"这家酒店可以容纳500位客人。"}]', 2000, 'hard'),
('benefit', '/ˈbenɪfɪt/', '[{"pos":"n.","def":"利益；好处"},{"pos":"vt.","def":"有益于"}]', '[{"en":"Regular exercise benefits your health.","cn":"经常锻炼有益于健康。"}]', 5000, 'easy'),
('comprehensive', '/ˌkɒmprɪˈhensɪv/', '[{"pos":"adj.","def":"综合的；全面的"}]', '[{"en":"We need a comprehensive plan.","cn":"我们需要一个全面的计划。"}]', 2200, 'medium'),
('demonstrate', '/ˈdemənstreɪt/', '[{"pos":"vt.","def":"证明；演示"},{"pos":"vi.","def":"示威"}]', '[{"en":"The experiment demonstrates the theory.","cn":"这个实验证明了这个理论。"}]', 2100, 'medium'),
('elaborate', '/ɪˈlæbərət/', '[{"pos":"adj.","def":"精心制作的"},{"pos":"vi.","def":"详细说明"}]', '[{"en":"Could you elaborate on your point?","cn":"你能详细说明你的观点吗？"}]', 1800, 'hard'),
('fundamental', '/ˌfʌndəˈmentl/', '[{"pos":"adj.","def":"基本的；根本的"},{"pos":"n.","def":"基本原理"}]', '[{"en":"This is a fundamental principle.","cn":"这是一个基本原则。"}]', 2800, 'medium'),
('generate', '/ˈdʒenəreɪt/', '[{"pos":"vt.","def":"产生；发生"}]', '[{"en":"The wind turbine generates electricity.","cn":"风力涡轮机产生电力。"}]', 2400, 'medium'),
('hypothesis', '/haɪˈpɒθəsɪs/', '[{"pos":"n.","def":"假设；假说"}]', '[{"en":"We need to test this hypothesis.","cn":"我们需要验证这个假设。"}]', 1500, 'hard'),
('illustrate', '/ˈɪləstreɪt/', '[{"pos":"vt.","def":"说明；阐明；给…加插图"}]', '[{"en":"Let me illustrate with an example.","cn":"让我用一个例子来说明。"}]', 2000, 'medium'),
('inevitable', '/ɪnˈevɪtəbl/', '[{"pos":"adj.","def":"不可避免的"}]', '[{"en":"Change is inevitable.","cn":"变化是不可避免的。"}]', 1900, 'medium'),
('legitimate', '/lɪˈdʒɪtɪmət/', '[{"pos":"adj.","def":"合法的；正当的"}]', '[{"en":"That is a legitimate concern.","cn":"那是一个正当的担忧。"}]', 1700, 'hard'),
('perspective', '/pəˈspektɪv/', '[{"pos":"n.","def":"观点；视角；透视"}]', '[{"en":"Try to see things from a different perspective.","cn":"试着从不同的角度看问题。"}]', 2600, 'medium'),
('significant', '/sɪɡˈnɪfɪkənt/', '[{"pos":"adj.","def":"重要的；有意义的；显著的"}]', '[{"en":"There has been a significant improvement.","cn":"有了显著的改善。"}]', 3500, 'easy');

-- =============================================
-- 18. word_story AI生成故事表
-- =============================================
CREATE TABLE word_story (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  title VARCHAR(64) DEFAULT NULL COMMENT '故事标题',
  content TEXT DEFAULT NULL COMMENT '故事正文',
  words VARCHAR(1024) DEFAULT NULL COMMENT '使用的单词列表(逗号分隔)',
  book_name VARCHAR(64) DEFAULT '默认生词本' COMMENT '所属词本',
  word_count INT DEFAULT 0 COMMENT '使用的目标单词数量',
  total_words INT DEFAULT 0 COMMENT '文本总词数',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_book_name (book_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI生成故事表';

-- =============================================
-- 19. 精读句子解析缓存表
-- =============================================

CREATE TABLE sentence_analysis_cache (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  sentence TEXT NOT NULL COMMENT '英文原文句子',
  sentence_hash VARCHAR(32) NOT NULL COMMENT '句子MD5哈希(用于快速索引匹配)',
  full_analysis LONGTEXT COMMENT 'AI完整解析原文',
  section1 TEXT COMMENT '第1段：原句重现与整体感知',
  section2 TEXT COMMENT '第2段：核心词汇与地道表达',
  section3 TEXT COMMENT '第3段：语法名词剖析',
  section4 TEXT COMMENT '第4段：深度解析与考点提示',
  translate_literal VARCHAR(512) DEFAULT NULL COMMENT '直译',
  translate_free VARCHAR(512) DEFAULT NULL COMMENT '意译',
  ai_model VARCHAR(32) DEFAULT NULL COMMENT 'AI模型标识(用于后续模型升级时区分缓存来源)',
  hit_count INT DEFAULT 0 COMMENT '缓存命中次数',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_hash (user_id, sentence_hash),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='精读句子解析缓存表';