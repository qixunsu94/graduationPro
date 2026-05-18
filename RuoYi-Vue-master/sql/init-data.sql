-- =============================================
-- 极研AI (GeekYan) - 初始化数据
-- =============================================

USE geekyan;

-- 管理员账号 (密码: admin123, BCrypt加密)
INSERT INTO sys_user (username, password, nickname, role, status) VALUES
('admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', 'admin', 1);

-- 默认角色
INSERT INTO sys_role (role_name, role_key, sort, status) VALUES
('超级管理员', 'admin', 1, 1),
('普通用户', 'user', 2, 1);

-- 管理端菜单
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, perms, icon, sort) VALUES
(0, '系统管理', 1, '/system', '', '', 'setting', 1),
(1, '用户管理', 2, '/system/user', 'system/user/index', 'system:user:list', 'user', 1),
(1, '角色管理', 2, '/system/role', 'system/role/index', 'system:role:list', 'peoples', 2),
(1, '菜单管理', 2, '/system/menu', 'system/menu/index', 'system:menu:list', 'tree-table', 3),
(0, '业务管理', 1, '/business', '', '', 'shopping', 2),
(5, '聊天记录', 2, '/business/chat', 'business/chat/index', 'business:chat:list', 'message', 1),
(5, '单词管理', 2, '/business/word', 'business/word/index', 'business:word:list', 'education', 2),
(5, '错题管理', 2, '/business/error', 'business/error/index', 'business:error:list', 'documentation', 3),
(5, '话题管理', 2, '/business/topic', 'business/topic/index', 'business:topic:list', 'skill', 4),
(5, '反馈管理', 2, '/business/feedback', 'business/feedback/index', 'business:feedback:list', 'email', 5),
(5, 'PDF文档', 2, '/business/pdf', 'business/pdf/index', 'business:pdf:list', 'document', 6),
(0, '数据统计', 1, '/analytics', '', '', 'chart', 3),
(12, '学情分析', 2, '/analytics/learning', 'analytics/learning/index', 'analytics:learning:list', 'chart', 1),
(12, '使用统计', 2, '/analytics/usage', 'analytics/usage/index', 'analytics:usage:list', 'data-line', 2);

-- 默认话题数据
INSERT INTO topic (name, description, icon, category, level, sort) VALUES
('日常对话', '练习日常生活中的英语对话', 'chat', '生活', 1, 1),
('商务英语', '练习商务场景中的英语表达', 'briefcase', '商务', 2, 2),
('旅游英语', '练习旅游相关的英语对话', 'map', '旅游', 1, 3),
('学术英语', '学术写作和讨论的英语表达', 'reading', '学术', 3, 4),
('面试英语', '求职面试场景的英语对话', 'suitcase', '职场', 2, 5),
('医疗英语', '医疗健康相关的英语表达', 'heart', '医疗', 3, 6);

-- 常用英语单词种子数据
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
