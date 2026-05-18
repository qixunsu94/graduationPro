-- =============================================
-- 极研AI (GeekYan) - 智能学习助手数据库建表脚本
-- 技术栈: MySQL 8.0 + Spring Boot + MyBatis-Plus + 若依框架
-- =============================================

CREATE DATABASE IF NOT EXISTS geekyan DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE geekyan;

-- =============================================
-- 1. 用户与权限表
-- =============================================

CREATE TABLE sys_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  username VARCHAR(64) DEFAULT NULL COMMENT '用户名',
  password VARCHAR(128) DEFAULT NULL COMMENT '密码',
  nickname VARCHAR(64) DEFAULT '学习者' COMMENT '昵称',
  avatar_url VARCHAR(512) DEFAULT '' COMMENT '头像地址',
  openid VARCHAR(128) DEFAULT NULL COMMENT '微信OpenID',
  phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  email VARCHAR(64) DEFAULT NULL COMMENT '邮箱',
  target_language VARCHAR(32) DEFAULT '英语' COMMENT '目标学习语言',
  native_language VARCHAR(32) DEFAULT '中文' COMMENT '母语',
  proficiency_level VARCHAR(32) DEFAULT '初级' COMMENT '语言水平',
  daily_goal INT DEFAULT 15 COMMENT '每日学习目标(分钟)',
  role VARCHAR(32) DEFAULT 'user' COMMENT '角色: user/admin',
  status TINYINT DEFAULT 1 COMMENT '状态: 0禁用 1正常',
  last_login_time DATETIME DEFAULT NULL COMMENT '最后登录时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除: 0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_openid (openid),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE sys_role (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
  role_key VARCHAR(64) NOT NULL COMMENT '角色标识',
  sort INT DEFAULT 0 COMMENT '排序',
  status TINYINT DEFAULT 1 COMMENT '状态: 0禁用 1正常',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE sys_menu (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  parent_id BIGINT DEFAULT 0 COMMENT '父菜单ID',
  menu_name VARCHAR(64) NOT NULL COMMENT '菜单名称',
  menu_type TINYINT NOT NULL COMMENT '类型: 1目录 2菜单 3按钮',
  path VARCHAR(256) DEFAULT '' COMMENT '路由地址',
  component VARCHAR(256) DEFAULT '' COMMENT '组件路径',
  perms VARCHAR(128) DEFAULT '' COMMENT '权限标识',
  icon VARCHAR(64) DEFAULT '' COMMENT '图标',
  sort INT DEFAULT 0 COMMENT '排序',
  visible TINYINT DEFAULT 1 COMMENT '是否可见: 0隐藏 1显示',
  status TINYINT DEFAULT 1 COMMENT '状态: 0禁用 1正常',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限表';

-- =============================================
-- 2. 聊天模块表
-- =============================================

CREATE TABLE chat_session (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  name VARCHAR(128) DEFAULT '日常对话' COMMENT '会话名称',
  role VARCHAR(64) DEFAULT '英语老师' COMMENT 'AI角色',
  topic VARCHAR(128) DEFAULT '日常对话' COMMENT '话题',
  session_type VARCHAR(32) DEFAULT 'TOPIC' COMMENT '会话类型',
  message_count INT DEFAULT 0 COMMENT '消息数量',
  last_message_time DATETIME DEFAULT NULL COMMENT '最后消息时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_id (session_id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

CREATE TABLE chat_message (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  message_id VARCHAR(64) NOT NULL COMMENT '消息ID',
  session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  content TEXT COMMENT '消息内容',
  role VARCHAR(16) NOT NULL COMMENT '角色: USER/ASSISTANT',
  file_name VARCHAR(256) DEFAULT NULL COMMENT '语音文件名',
  grammar_analysis TEXT DEFAULT NULL COMMENT '语法分析',
  translation TEXT DEFAULT NULL COMMENT '翻译',
  pronunciation_score INT DEFAULT NULL COMMENT '发音评分',
  send_message_id VARCHAR(64) DEFAULT NULL COMMENT '关联的用户消息ID',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_message_id (message_id),
  KEY idx_session_id (session_id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- =============================================
-- 3. 单词模块表
-- =============================================

CREATE TABLE word (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  word VARCHAR(128) NOT NULL COMMENT '单词',
  phonetic VARCHAR(128) DEFAULT '' COMMENT '音标',
  meanings JSON DEFAULT NULL COMMENT '释义列表(JSON)',
  examples JSON DEFAULT NULL COMMENT '例句列表(JSON)',
  frequency INT DEFAULT 0 COMMENT '词频',
  difficulty VARCHAR(16) DEFAULT 'medium' COMMENT '难度: easy/medium/hard',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_word (word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词表';

CREATE TABLE word_book (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  word_id BIGINT NOT NULL COMMENT '单词ID',
  word VARCHAR(128) NOT NULL COMMENT '单词(冗余)',
  book_name VARCHAR(64) DEFAULT '默认生词本' COMMENT '生词本名称',
  mastery_level TINYINT DEFAULT 0 COMMENT '掌握程度: 0新词 1认识 2熟悉 3掌握',
  review_count INT DEFAULT 0 COMMENT '复习次数',
  last_review_time DATETIME DEFAULT NULL COMMENT '最后复习时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_word_id (word_id),
  UNIQUE KEY uk_user_word (user_id, word_id, book_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词本表';

-- =============================================
-- 4. 错题模块表
-- =============================================

CREATE TABLE error_record (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  subject VARCHAR(32) DEFAULT 'english' COMMENT '科目: english/math/cs',
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
-- 5. 艾宾浩斯复习模块表
-- =============================================

CREATE TABLE review_task (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  related_type VARCHAR(32) NOT NULL COMMENT '关联类型: word/error_record',
  related_id BIGINT NOT NULL COMMENT '关联ID',
  content TEXT NOT NULL COMMENT '复习内容',
  review_stage INT DEFAULT 1 COMMENT '复习阶段(1-6)',
  next_review_time DATETIME NOT NULL COMMENT '下次复习时间',
  last_review_time DATETIME DEFAULT NULL COMMENT '上次复习时间',
  is_completed TINYINT DEFAULT 0 COMMENT '是否已完成: 0否 1是',
  accuracy_score INT DEFAULT NULL COMMENT '正确率评分',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_next_review (user_id, next_review_time, is_completed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='艾宾浩斯复习任务表';

-- =============================================
-- 6. 学习记录表
-- =============================================

CREATE TABLE learning_record (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  record_type VARCHAR(32) NOT NULL COMMENT '类型: chat/word_search/practice/review',
  duration INT DEFAULT 0 COMMENT '学习时长(秒)',
  content_summary VARCHAR(512) DEFAULT '' COMMENT '内容摘要',
  score INT DEFAULT NULL COMMENT '得分',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_type (record_type),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习记录表';

-- =============================================
-- 7. PDF文档表
-- =============================================

CREATE TABLE pdf_document (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  file_name VARCHAR(256) NOT NULL COMMENT '文件名',
  file_path VARCHAR(512) NOT NULL COMMENT '文件路径',
  file_size BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
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
-- 8. 长难句表
-- =============================================

CREATE TABLE long_sentence (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  sentence TEXT NOT NULL COMMENT '英文句子',
  translation TEXT DEFAULT NULL COMMENT '中文翻译',
  analysis TEXT DEFAULT NULL COMMENT '语法分析',
  source VARCHAR(128) DEFAULT '' COMMENT '来源',
  difficulty VARCHAR(16) DEFAULT 'medium' COMMENT '难度',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='长难句表';

-- =============================================
-- 9. 话题表
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
-- 10. 用户设置表
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
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设置表';

-- =============================================
-- 11. 反馈表
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
-- 12. 收藏表
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
