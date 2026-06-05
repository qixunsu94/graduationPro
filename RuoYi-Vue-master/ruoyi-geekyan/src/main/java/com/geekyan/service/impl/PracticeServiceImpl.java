package com.geekyan.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geekyan.entity.AiNote;
import com.geekyan.entity.ChatMessage;
import com.geekyan.entity.ChatSession;
import com.geekyan.entity.ReviewTask;
import com.geekyan.mapper.AiNoteMapper;
import com.geekyan.mapper.ChatMessageMapper;
import com.geekyan.mapper.ChatSessionMapper;
import com.geekyan.mapper.ReviewTaskMapper;
import com.geekyan.service.IAiService;
import com.geekyan.service.ILearningRecordService;
import com.geekyan.service.IPracticeService;
import com.geekyan.service.SubjectResolver;
import com.geekyan.service.SubjectResolver.Subject;
import com.geekyan.util.AiTextCleaner;
import com.geekyan.vo.PracticeEndResponse;
import com.geekyan.vo.PracticeStartResponse;
import com.ruoyi.common.utils.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI对练Service实现 - 注入用户个人数据实现个性化对练
 */
@Service
public class PracticeServiceImpl implements IPracticeService {

        private static final Logger log = LoggerFactory.getLogger(PracticeServiceImpl.class);

        @Autowired
        private IAiService aiService;

        @Autowired
        private ChatMessageMapper chatMessageMapper;

        @Autowired
        private ReviewTaskMapper reviewTaskMapper;

        @Autowired
        private AiNoteMapper aiNoteMapper;

        @Autowired
        private ChatSessionMapper chatSessionMapper;

        @Autowired
        private SubjectResolver subjectResolver;

        @Autowired
        private ILearningRecordService learningRecordService;

        @Override
        public PracticeStartResponse startPractice(String topic, String subject) {
                Long userId = SecurityUtils.getUserId();

                // 1. 解析主题，自动识别学科和角色
                Subject resolvedSubject = (subject != null && !subject.isEmpty() && !"general".equals(subject))
                                ? Subject.fromCode(subject)
                                : subjectResolver.resolveSubject(topic);
                String resolvedSubjectCode = resolvedSubject.getCode();
                String resolvedRole = resolvedSubject.getRole();
                String resolvedLabel = resolvedSubject.getLabel();

                log.info("对练主题解析: topic={}, resolvedSubject={}, role={}", topic, resolvedSubjectCode, resolvedRole);

                // 2. 数据聚合：根据解析后的学科从数据库搜集用户的相关学习数据
                Map<String, Object> userProfile = aggregateUserProfile(userId, topic, resolvedSubjectCode);
                String userProfileJson = JSON.toJSONString(userProfile, JSONWriter.Feature.PrettyFormat);

                // 3. 构建学科专属对练Prompt
                String practicePrompt = buildSubjectPracticePrompt(resolvedSubject, topic, userProfileJson);

                // 4. 创建新的对练会话并调用AI
                String sessionId = "practice-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                String sessionTitle = "AI对练: " + topic;

                String firstQuestion = cleanPracticeText(aiService.chat(practicePrompt, "我准备好了，请开始提问。", sessionId));

                // 5. 在数据库中创建ChatSession记录
                ChatSession chatSession = new ChatSession();
                chatSession.setSessionId(sessionId);
                chatSession.setUserId(userId);
                chatSession.setName(sessionTitle);
                chatSession.setTopic(topic);
                chatSession.setSessionType("PRACTICE");
                chatSession.setSubject(resolvedSubjectCode); // 使用解析后的学科
                chatSession.setRole(resolvedRole); // 使用解析后的角色
                chatSession.setMessageCount(0);
                chatSession.setCreateTime(LocalDateTime.now());
                chatSession.setUpdateTime(LocalDateTime.now());
                chatSessionMapper.insert(chatSession);

                // 6. 保存AI的第一条消息
                ChatMessage aiMsg = new ChatMessage();
                aiMsg.setMessageId(UUID.randomUUID().toString().replace("-", ""));
                aiMsg.setSessionId(sessionId);
                aiMsg.setUserId(userId);
                aiMsg.setContent(firstQuestion);
                aiMsg.setRole("ASSISTANT");
                aiMsg.setSubject(resolvedSubjectCode);
                aiMsg.setCreateTime(LocalDateTime.now());
                aiMsg.setUpdateTime(LocalDateTime.now());
                chatMessageMapper.insert(aiMsg);

                // 7. 返回会话ID和AI的第一个问题
                return new PracticeStartResponse(sessionId, firstQuestion, resolvedSubjectCode, resolvedRole,
                                resolvedLabel);
        }

        @Override
        public Map<String, Object> answerPractice(String sessionId, String message, String imageBase64) {
                Long userId = SecurityUtils.getUserId();
                ChatSession session = chatSessionMapper.selectOne(
                                new LambdaQueryWrapper<ChatSession>()
                                                .eq(ChatSession::getSessionId, sessionId)
                                                .eq(ChatSession::getUserId, userId)
                                                .last("LIMIT 1"));
                if (session == null) {
                        throw new IllegalArgumentException("未找到对练会话");
                }

                String subjectCode = session.getSubject() != null ? session.getSubject() : "general";
                Subject subject = Subject.fromCode(subjectCode);
                String topic = session.getTopic() != null ? session.getTopic().replace("AI对练: ", "") : "AI对练";

                List<ChatMessage> history = chatMessageMapper.selectList(
                                new LambdaQueryWrapper<ChatMessage>()
                                                .eq(ChatMessage::getSessionId, sessionId)
                                                .eq(ChatMessage::getUserId, userId)
                                                .orderByAsc(ChatMessage::getCreateTime));

                Map<String, Object> userProfile = aggregateUserProfile(userId, topic, subjectCode);
                String userProfileJson = JSON.toJSONString(userProfile, JSONWriter.Feature.PrettyFormat);
                String systemPrompt = buildSubjectPracticePrompt(subject, topic, userProfileJson)
                                + "\n\n## 继续对练时的强制规则\n"
                                + "你正在延续同一场AI对练，不是在做普通知识讲解。\n"
                                + "必须先判断学生刚才对上一题的回答是否正确，再进入下一步。\n"
                                + "每次回复只能包含：一句反馈 + 一个追问或下一题。\n"
                                + "回答正确时，不展开长篇定义，最多一句肯定后立刻追问更深一层。\n"
                                + "回答错误或不完整时，只指出关键缺口并给一个引导问题，除非学生明确说跳过，否则不要直接给完整答案。\n"
                                + "回复必须控制在200字以内。\n"
                                + "禁止使用LaTeX、美元符号公式标记、反斜杠公式命令；变量直接写成 v_i、G=(V,E)、sum TD(v)=2e 这种纯文本。";

                String userPrompt = buildPracticeTurnPrompt(history, message,
                                imageBase64 != null && !imageBase64.isEmpty());
                String rawReply = (imageBase64 != null && !imageBase64.isEmpty())
                                ? aiService.chatWithImagePrompt(systemPrompt, userPrompt, imageBase64, sessionId)
                                : aiService.chat(systemPrompt, userPrompt, sessionId);
                String aiReply = cleanPracticeText(rawReply);
                if (aiReply == null || aiReply.isEmpty()) {
                        throw new IllegalStateException("AI对练暂时不可用");
                }

                ChatMessage userMsg = new ChatMessage();
                String userMessageId = UUID.randomUUID().toString().replace("-", "");
                userMsg.setMessageId(userMessageId);
                userMsg.setSessionId(sessionId);
                userMsg.setUserId(userId);
                userMsg.setContent(message != null && !message.trim().isEmpty() ? message : "已上传图片作答");
                userMsg.setRole("USER");
                userMsg.setSubject(subjectCode);
                if (imageBase64 != null && !imageBase64.isEmpty()) {
                        userMsg.setImageUrl("base64_upload");
                }
                userMsg.setCreateTime(LocalDateTime.now());
                userMsg.setUpdateTime(LocalDateTime.now());
                chatMessageMapper.insert(userMsg);

                ChatMessage aiMsg = new ChatMessage();
                String aiMessageId = UUID.randomUUID().toString().replace("-", "");
                aiMsg.setMessageId(aiMessageId);
                aiMsg.setSessionId(sessionId);
                aiMsg.setUserId(userId);
                aiMsg.setContent(aiReply);
                aiMsg.setRole("ASSISTANT");
                aiMsg.setSendMessageId(userMessageId);
                aiMsg.setSubject(subjectCode);
                aiMsg.setCreateTime(LocalDateTime.now());
                aiMsg.setUpdateTime(LocalDateTime.now());
                chatMessageMapper.insert(aiMsg);

                session.setMessageCount(history.size() + 2);
                chatSessionMapper.updateById(session);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("message_id", aiMessageId);
                result.put("send_message_id", userMessageId);
                result.put("sessionId", sessionId);
                result.put("subject", subjectCode);
                result.put("role", session.getRole());
                result.put("reply", aiReply);
                result.put("content", aiReply);
                result.put("data", aiReply);
                return result;
        }

        private String buildPracticeTurnPrompt(List<ChatMessage> history, String latestAnswer, boolean hasImage) {
                StringBuilder sb = new StringBuilder();
                sb.append("下面是当前AI对练的最近历史，请据此判断学生最新回答，并继续一问一答。\n\n");
                int start = Math.max(0, history.size() - 12);
                for (int i = start; i < history.size(); i++) {
                        ChatMessage msg = history.get(i);
                        String role = "USER".equalsIgnoreCase(msg.getRole()) ? "学生" : "AI";
                        sb.append(role).append("：").append(truncateForPrompt(msg.getContent(), 260)).append("\n");
                }
                sb.append("\n学生最新回答：").append(latestAnswer != null && !latestAnswer.trim().isEmpty()
                                ? latestAnswer.trim()
                                : "学生上传了图片作答，请结合图片判断。");
                if (hasImage) {
                        sb.append("\n学生本轮还上传了一张图片，请识别其中的作答内容。");
                }
                sb.append("\n\n请输出：一句反馈 + 一个追问或下一题。不要写长篇讲解，不要列多段，不要输出JSON。");
                return sb.toString();
        }

        private String truncateForPrompt(String value, int maxLength) {
                if (value == null) {
                        return "";
                }
                String text = value.replace("\r\n", "\n").trim();
                return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
        }

        private String cleanPracticeText(String value) {
                String cleaned = AiTextCleaner.clean(value);
                if (cleaned == null) {
                        return null;
                }
                return cleaned
                                .replaceAll("\\$([^$\\n]{1,240})\\$", "$1")
                                .replace("\\(", "")
                                .replace("\\)", "")
                                .replace("\\[", "")
                                .replace("\\]", "")
                                .replaceAll("\\\\frac\\{([^{}]+)\\}\\{([^{}]+)\\}", "($1)/($2)")
                                .replaceAll("\\\\sum\\s*", "sum ")
                                .replaceAll("\\\\int\\s*", "int ")
                                .replaceAll("\\\\lim\\s*", "lim ")
                                .replace("\\", "")
                                .replaceAll("[ \\t]+\\n", "\n")
                                .replaceAll("\\n{3,}", "\n\n")
                                .trim();
        }

        /**
         * 聚合用户学习档案数据 - 增强版
         */
        private Map<String, Object> aggregateUserProfile(Long userId, String topic, String subject) {
                Map<String, Object> profile = new HashMap<>();

                // 1. 从聊天记录中获取相关历史
                List<ChatMessage> chatMessages = chatMessageMapper.selectList(
                                new LambdaQueryWrapper<ChatMessage>()
                                                .eq(ChatMessage::getUserId, userId)
                                                .eq(ChatMessage::getSubject, subject)
                                                .and(w -> w.like(ChatMessage::getContent, topic)
                                                                .or().like(ChatMessage::getContent, subject))
                                                .orderByDesc(ChatMessage::getCreateTime)
                                                .last("LIMIT 10"));
                List<String> chatHistory = chatMessages.stream()
                                .map(msg -> (msg.getRole().equals("USER") ? "用户曾问：" : "AI曾答：") +
                                                (msg.getContent() != null && msg.getContent().length() > 100
                                                                ? msg.getContent().substring(0, 100) + "..."
                                                                : msg.getContent()))
                                .collect(Collectors.toList());
                profile.put("related_chat_history", chatHistory);

                // 2. 从复习任务中获取掌握度 - 区分薄弱和已掌握
                List<ReviewTask> allReviewTasks = reviewTaskMapper.selectList(
                                new LambdaQueryWrapper<ReviewTask>()
                                                .eq(ReviewTask::getUserId, userId)
                                                .eq(ReviewTask::getSubject, subject)
                                                .eq(ReviewTask::getDelFlag, 0));

                List<String> weakPoints = new ArrayList<>();
                List<String> strongPoints = new ArrayList<>();
                double totalMastery = 0;
                int taskCount = allReviewTasks.size();

                for (ReviewTask task : allReviewTasks) {
                        double mastery = task.getMasteryLevel() != null ? task.getMasteryLevel() : 0.0;
                        totalMastery += mastery;
                        String content = task.getContent();
                        if (content != null && !content.isEmpty()) {
                                if (mastery < 0.6) {
                                        weakPoints.add(content.length() > 30 ? content.substring(0, 30) : content);
                                } else if (mastery >= 0.8) {
                                        strongPoints.add(content.length() > 30 ? content.substring(0, 30) : content);
                                }
                        }
                }

                double averageMastery = taskCount > 0 ? totalMastery / taskCount : -1.0;
                profile.put("mastery_level", Math.round(averageMastery * 100.0) / 100.0);
                profile.put("review_task_count", taskCount);
                profile.put("weak_points", weakPoints);
                profile.put("strong_points", strongPoints);

                // 3. 从笔记中获取相关摘要和知识点标签
                List<AiNote> notes = aiNoteMapper.selectList(
                                new LambdaQueryWrapper<AiNote>()
                                                .eq(AiNote::getUserId, userId)
                                                .eq(AiNote::getSubject, subject)
                                                .and(wrapper -> wrapper
                                                                .like(AiNote::getTitle, topic)
                                                                .or().like(AiNote::getQuestionText, topic)
                                                                .or().like(AiNote::getKeyPoints, topic)
                                                                .or().like(AiNote::getKnowledgeTags, topic))
                                                .orderByDesc(AiNote::getCreateTime)
                                                .last("LIMIT 10"));

                List<String> noteSummaries = new ArrayList<>();
                Set<String> allKnowledgeTags = new LinkedHashSet<>();
                Set<String> trapTypes = new LinkedHashSet<>();

                for (AiNote note : notes) {
                        String summary = "";
                        if (note.getTitle() != null)
                                summary += note.getTitle();
                        if (note.getKeyPoints() != null && !note.getKeyPoints().isEmpty()) {
                                summary += " | 要点: " + (note.getKeyPoints().length() > 80
                                                ? note.getKeyPoints().substring(0, 80) + "..."
                                                : note.getKeyPoints());
                        }
                        if (!summary.isEmpty())
                                noteSummaries.add(summary);

                        // 提取知识点标签
                        if (note.getKnowledgeTags() != null && !note.getKnowledgeTags().isEmpty()) {
                                try {
                                        List<String> tags = JSON.parseArray(note.getKnowledgeTags(), String.class);
                                        if (tags != null)
                                                allKnowledgeTags.addAll(tags);
                                } catch (Exception ignored) {
                                }
                        }
                        if (note.getTrapTypes() != null && !note.getTrapTypes().isEmpty()) {
                                trapTypes.add(note.getTrapTypes());
                        }
                }

                profile.put("related_notes", noteSummaries);
                profile.put("knowledge_tags", new ArrayList<>(allKnowledgeTags));
                profile.put("trap_types", new ArrayList<>(trapTypes));

                return profile;
        }

        /**
         * 构建学科专属对练Prompt
         */
        private String buildSubjectPracticePrompt(Subject subject, String topic, String userProfileJson) {
                String prompt;
                switch (subject) {
                        case MATH:
                                prompt = buildMathPrompt(topic, userProfileJson);
                                break;
                        case DATA_STRUCTURE:
                                prompt = buildDSPrompt(topic, userProfileJson);
                                break;
                        case COMPUTER_ORGANIZATION:
                                prompt = buildCOPrompt(topic, userProfileJson);
                                break;
                        case OS:
                                prompt = buildOSPrompt(topic, userProfileJson);
                                break;
                        case NETWORK:
                                prompt = buildCNPrompt(topic, userProfileJson);
                                break;
                        case ENGLISH:
                                prompt = buildEnglishPrompt(topic, userProfileJson);
                                break;
                        default:
                                prompt = buildGeneralPrompt(topic, userProfileJson);
                                break;
                }
                return prompt + commonPracticeOutputRules();
        }

        private String commonPracticeOutputRules() {
                return "\n\n## 真正对练输出规则（必须遵守）\n"
                                + "1. 这是一问一答训练，不是普通知识讲解。每次只允许提出一个问题。\n"
                                + "2. 回复结构固定为：一句反馈/开场 + 一个问题。不要输出多段讲义。\n"
                                + "3. 学生答对时，只用一句话确认关键点，然后立刻追问更深一层。\n"
                                + "4. 学生答错或不完整时，只指出一个最关键缺口，并用问题引导，不直接给完整答案。\n"
                                + "5. 除非学生说“跳过”或连续多次答不出，否则不要给大段解析。\n"
                                + "6. 每次回复不超过200字，语气像面试官追问，紧凑、有互动感。\n"
                                + "7. 禁止使用LaTeX、$...$、\\\\sum、\\\\frac、\\\\(\\\\) 等公式标记；变量和公式用纯文本，如 v_i、G=(V,E)、sum TD(v)=2e。\n";
        }

        private String buildMathPrompt(String topic, String profileJson) {
                return String.format(
                                "你是一位考研数学辅导专家，正在与一位考研学生进行一对一的对练辅导。\n\n" +
                                                "## 对练主题\n%s\n\n" +
                                                "## 学生当前学习情况\n```json\n%s\n```\n\n" +
                                                "## 对练规则\n" +
                                                "1. 每次只问一道题，等学生回答后再出下一题。\n" +
                                                "2. 题目难度从基础开始，逐步提升。如果学生回答正确，下一题难度适当增加。\n" +
                                                "3. 优先覆盖学生的薄弱知识点。\n" +
                                                "4. 每轮对练共5-8题，覆盖主题的核心内容。\n" +
                                                "5. 你出的题目类型包括：计算题、证明题、概念判断题。\n\n" +
                                                "## 学生作答方式\n" +
                                                "- 学生可能打字描述推导过程，也可能拍照上传手写过程。\n" +
                                                "- 如果学生上传图片，你需要识别图片中的推导步骤，判断逻辑是否正确。\n" +
                                                "- 如果学生只给答案不给过程，要求他补充推导过程。\n\n" +
                                                "## 评估规则\n" +
                                                "- 回答正确且过程完整：简短肯定（如\"正确！\"），然后进入下一题。\n" +
                                                "- 回答思路正确但有小错误：指出具体错误，让学生修正，不直接给答案。\n" +
                                                "- 回答错误：给出一个提示或引导性问题，帮助学生自己找到正确思路。\n" +
                                                "- 学生说\"跳过\"：直接给出正确答案和完整推导过程，标记该知识点为薄弱点，然后进入下一题。\n\n" +
                                                "## 格式要求\n" +
                                                "- 数学公式用纯文本描述，禁止使用LaTeX语法。\n\n" +
                                                "现在请根据以上信息，开始对练。",
                                topic, profileJson);
        }

        private String buildDSPrompt(String topic, String profileJson) {
                return String.format(
                                "你是一位数据结构辅导专家，正在与一位考研学生进行一对一的对练辅导。\n\n" +
                                                "## 对练主题\n%s\n\n" +
                                                "## 学生当前学习情况\n```json\n%s\n```\n\n" +
                                                "## 对练规则\n" +
                                                "1. 每次只问一道题，等学生回答后再出下一题。\n" +
                                                "2. 题目难度从基础开始。正确率高的知识点快速过，薄弱点多花时间。\n" +
                                                "3. 优先覆盖学生的薄弱知识点。\n" +
                                                "4. 每轮对练共5-8题。\n\n" +
                                                "## 出题类型\n" +
                                                "- 概念理解题：问定义、特点、适用场景（如\"什么是完全二叉树？\"）\n" +
                                                "- 算法设计题：让学生描述算法思路或写伪代码（如\"如何非递归实现中序遍历？\"）\n" +
                                                "- 对比分析题：让学生对比两个概念（如\"BFS和DFS的区别\"）\n" +
                                                "- 复杂度分析题：让学生分析时间/空间复杂度\n\n" +
                                                "## 学生作答方式\n" +
                                                "- 学生可能打字描述思路，也可能拍照上传手写伪代码。\n" +
                                                "- 如果学生上传图片，识别图中的代码或思路，判断逻辑是否正确。\n\n" +
                                                "## 评估规则\n" +
                                                "- 概念题：判断定义是否准确，关键点是否遗漏。\n" +
                                                "- 算法题：判断逻辑是否正确，边界条件是否考虑，复杂度分析是否合理。\n" +
                                                "- 回答正确：简短肯定后出下一题。\n" +
                                                "- 回答有误：指出错误点，给出提示，让学生修正。\n" +
                                                "- 学生说\"跳过\"：给出正确答案和详细解释，标记该知识点。\n\n" +
                                                "现在请根据以上信息，开始对练。",
                                topic, profileJson);
        }

        private String buildCOPrompt(String topic, String profileJson) {
                return build408Prompt("计算机组成原理", "组成原理辅导专家", topic, profileJson);
        }

        private String buildOSPrompt(String topic, String profileJson) {
                return build408Prompt("操作系统", "操作系统辅导专家", topic, profileJson);
        }

        private String buildCNPrompt(String topic, String profileJson) {
                return build408Prompt("计算机网络", "计算机网络辅导专家", topic, profileJson);
        }

        /**
         * 计组/OS/计网三科共用模板
         */
        private String build408Prompt(String subjectName, String role, String topic, String profileJson) {
                return String.format(
                                "你是一位%s，正在与一位考研学生进行一对一的对练辅导。\n\n" +
                                                "## 对练主题\n%s\n\n" +
                                                "## 学生当前学习情况\n```json\n%s\n```\n\n" +
                                                "## 对练规则\n" +
                                                "1. 每次只问一道题，等学生回答后再出下一题。\n" +
                                                "2. 优先覆盖学生的薄弱知识点。\n" +
                                                "3. 每轮对练共5-8题。\n\n" +
                                                "## 出题类型\n" +
                                                "- 概念理解题：问定义、原理、工作机制（如\"什么是虚拟内存？\"）\n" +
                                                "- 对比分析题：对比两个易混淆概念（如\"分页和分段的区别\"）\n" +
                                                "- 计算题：地址映射计算、Cache命中率、缺页率等\n" +
                                                "- 状态推演题：描述某个操作过程中状态的变化（如\"TCP三次握手过程\"）\n" +
                                                "- 关联题：跨科关联知识点（如\"MMU在计组和OS中的作用\"）\n\n" +
                                                "## 评估规则\n" +
                                                "- 概念题：判断是否准确、关键点是否遗漏。\n" +
                                                "- 对比题：判断对比维度是否全面，区别点是否清晰。\n" +
                                                "- 计算题：判断步骤和结果是否正确。\n" +
                                                "- 回答正确：简短肯定后出下一题。\n" +
                                                "- 回答有误：指出错误点，给出提示。\n" +
                                                "- 学生说\"跳过\"：给出正确答案和详细解释。\n\n" +
                                                "现在请根据以上信息，开始对练。",
                                role, topic, profileJson);
        }

        private String buildEnglishPrompt(String topic, String profileJson) {
                return String.format(
                                "你是一位考研英语辅导老师，正在与一位考研学生进行一对一的口语和语法对练。\n\n" +
                                                "## 对练主题\n%s\n\n" +
                                                "## 学生当前学习情况\n```json\n%s\n```\n\n" +
                                                "## 对练规则\n" +
                                                "1. 每次出一道题或问一个问题，等学生回答后再出下一题。\n" +
                                                "2. 每轮对练共5-8轮。\n" +
                                                "3. 出题类型包括：\n" +
                                                "   - 单词拼写/释义：你说中文，学生拼写英文\n" +
                                                "   - 语法填空：给句子，让学生填正确的词形或时态\n" +
                                                "   - 口语表达：让学生用英语口述一段话\n" +
                                                "   - 翻译：给中文句子，让学生翻译成英文\n\n" +
                                                "## 学生作答方式\n" +
                                                "- 学生可能打字回答，也可能用录音回答（特别是口语题）。\n" +
                                                "- 录音回答时，你需要评估发音准确度、流利度、语调。\n" +
                                                "- 打字回答时，评估拼写、语法、用词。\n\n" +
                                                "## 评估规则\n" +
                                                "- 完全正确：简短肯定（如\"Correct! Well done!\"），然后下一题。\n" +
                                                "- 有小错误：指出错误（如\"注意时态，应该是过去式\"），让学生修正。\n" +
                                                "- 有较大错误：给出正确用法和例句，让学生再试一次。\n" +
                                                "- 学生说\"skip\"：给出正确答案和详细解释。\n\n" +
                                                "Please ask the first question.",
                                topic, profileJson);
        }

        private String buildGeneralPrompt(String topic, String profileJson) {
                return String.format(
                                "你是一位通用AI助手，正在与一位考研学生进行一对一的对练辅导。\n\n" +
                                                "## 对练主题\n%s\n\n" +
                                                "## 学生当前学习情况\n```json\n%s\n```\n\n" +
                                                "## 对练规则\n" +
                                                "1. 每次只问一个问题，等学生回答后再问下一个。\n" +
                                                "2. 题目难度从基础开始，根据学生回答情况逐步调整。\n" +
                                                "3. 优先覆盖学生的薄弱知识点。\n" +
                                                "4. 每轮对练共5-8轮，覆盖主题的核心内容。\n" +
                                                "5. 当学生回答\"不知道\"或\"跳过\"时，记录该知识点，切换到下一个。\n\n" +
                                                "## 评估规则\n" +
                                                "- 回答正确：简短肯定后进入下一个知识点。\n" +
                                                "- 回答不完整：先给出提示引导思考，不要直接给答案。\n" +
                                                "- 回答错误：温和指出问题，给出正确思路，降低难度重新提问。\n\n" +
                                                "现在请根据以上信息，开始对练。",
                                topic, profileJson);
        }

        @Override
        public PracticeEndResponse endPractice(String sessionId) {
                Long userId = SecurityUtils.getUserId();

                // 1. 获取该对练会话的所有消息
                List<ChatMessage> messages = chatMessageMapper.selectList(
                                new LambdaQueryWrapper<ChatMessage>()
                                                .eq(ChatMessage::getSessionId, sessionId)
                                                .eq(ChatMessage::getUserId, userId)
                                                .orderByAsc(ChatMessage::getCreateTime));

                if (messages.isEmpty()) {
                        PracticeEndResponse resp = new PracticeEndResponse();
                        resp.setScore(0);
                        resp.setStrengths("无对练记录");
                        resp.setWeaknesses("无对练记录");
                        resp.setSuggestions("请先进行对练再结束");
                        resp.setRounds(0);
                        return resp;
                }

                // 2. 统计用户回答轮次
                long userRounds = messages.stream()
                                .filter(msg -> "user".equalsIgnoreCase(msg.getRole()) || "USER".equals(msg.getRole()))
                                .count();

                // 3. 构建对话摘要，让AI评估
                StringBuilder dialogueSummary = new StringBuilder();
                for (ChatMessage msg : messages) {
                        String role = "user".equalsIgnoreCase(msg.getRole()) ? "用户" : "AI";
                        String content = msg.getContent();
                        if (content != null && content.length() > 150) {
                                content = content.substring(0, 150) + "...";
                        }
                        dialogueSummary.append(role).append("：").append(content).append("\n");
                }

                String evalPrompt = String.format(
                                "你是一位严格但具体的考研辅导评估专家。请根据以下AI对练的完整对话记录，给出课后评估报告。\n\n" +
                                                "## 对话记录：\n%s\n\n" +
                                                "## 评估要求：\n" +
                                                "1. 只评价学生真实回答过的内容，不要泛泛鼓励。\n" +
                                                "2. strengths 要指出已经掌握的具体知识点或思维动作。\n" +
                                                "3. weaknesses 要指出最需要补的一个关键漏洞。\n" +
                                                "4. suggestions 要给出下一步可执行练习建议，具体到知识点和练习方式。\n" +
                                                "5. 如果对练轮次太少，评分要保守，并在建议中提醒继续完成更多轮次。\n\n" +
                                                "请严格按以下JSON格式返回，不要返回其他内容：\n" +
                                                "{\"score\":数字(0-100的综合评分),\"strengths\":\"用户表现好的方面（1-2句话）\",\"weaknesses\":\"用户薄弱的方面（1-2句话）\",\"suggestions\":\"具体的改进建议（1-2句话）\"}",
                                dialogueSummary.toString());

                String evalResult = aiService.chatWithoutHistory("你是考研辅导评估专家，只返回JSON格式。", evalPrompt);

                // 4. 解析AI评估结果
                PracticeEndResponse response = new PracticeEndResponse();
                response.setRounds((int) userRounds);

                try {
                        String jsonStr = evalResult;
                        if (jsonStr.contains("{")) {
                                jsonStr = jsonStr.substring(jsonStr.indexOf("{"), jsonStr.lastIndexOf("}") + 1);
                        }
                        JSONObject jsonObj = JSON.parseObject(jsonStr);
                        response.setScore(jsonObj.getInteger("score"));
                        response.setStrengths(jsonObj.getString("strengths"));
                        response.setWeaknesses(jsonObj.getString("weaknesses"));
                        response.setSuggestions(jsonObj.getString("suggestions"));
                } catch (Exception e) {
                        log.warn("解析AI评估结果失败，使用默认值", e);
                        response.setScore(50);
                        response.setStrengths("已完成对练");
                        response.setWeaknesses("评估解析失败");
                        response.setSuggestions("建议继续加强练习");
                }

                // 5. 从会话表获取对练主题
                ChatSession session = chatSessionMapper.selectOne(
                                new LambdaQueryWrapper<ChatSession>()
                                                .eq(ChatSession::getSessionId, sessionId)
                                                .eq(ChatSession::getUserId, userId)
                                                .last("LIMIT 1"));
                if (session != null && session.getTopic() != null) {
                        response.setTopic(session.getTopic());
                } else if (session != null && session.getName() != null) {
                        response.setTopic(session.getName().replace("AI对练: ", ""));
                }

                // 6. 更新相关复习任务的掌握度
                int finalScore = response.getScore() != null ? response.getScore() : 50;
                response.setScore(finalScore);
                updateReviewTaskMastery(userId, session, finalScore);

                savePracticeReport(userId, session, response);

                try {
                        learningRecordService.recordLearning(userId, "practice", null,
                                        response.getTopic(), response.getScore(),
                                        session != null ? session.getSubject() : null, "practice", sessionId);
                } catch (Exception e) {
                        log.warn("记录对练学习行为失败: {}", e.getMessage());
                }

                return response;
        }

        private void savePracticeReport(Long userId, ChatSession session, PracticeEndResponse response) {
                if (session == null) {
                        return;
                }
                AiNote existing = aiNoteMapper.selectOne(new LambdaQueryWrapper<AiNote>()
                                .eq(AiNote::getUserId, userId)
                                .eq(AiNote::getSessionId, session.getSessionId())
                                .eq(AiNote::getNoteType, "practice_report")
                                .last("LIMIT 1"));

                String topic = response.getTopic() != null ? response.getTopic()
                                : (session.getTopic() != null ? session.getTopic() : session.getName());
                String reportContent = AiTextCleaner.clean(buildPracticeReportContent(response));

                AiNote note = existing != null ? existing : new AiNote();
                note.setUserId(userId);
                note.setSessionId(session.getSessionId());
                note.setSubject(session.getSubject() != null ? session.getSubject() : "general");
                note.setTitle("AI对练报告: " + (topic != null ? topic : "未命名主题"));
                note.setQuestionText(topic);
                note.setAiContent(reportContent);
                note.setKeyPoints(AiTextCleaner.clean(response.getSuggestions()));
                note.setKnowledgeTags(JSON.toJSONString(Collections.singletonList(topic != null ? topic : "AI对练")));
                note.setNoteType("practice_report");
                note.setSourceType("practice");
                note.setSourceId(session.getId());
                note.setIsAutoExtracted(1);
                if (existing != null) {
                        note.setUpdateTime(LocalDateTime.now());
                        aiNoteMapper.updateById(note);
                } else {
                        note.setCreateTime(LocalDateTime.now());
                        note.setUpdateTime(LocalDateTime.now());
                        aiNoteMapper.insert(note);
                }
        }

        private String buildPracticeReportContent(PracticeEndResponse response) {
                return "本轮对练评估报告\n\n" +
                                "主题：" + safeText(response.getTopic(), "未命名主题") + "\n" +
                                "轮次：" + response.getRounds() + "\n" +
                                "综合评分：" + response.getScore() + "/100\n\n" +
                                "表现出色：" + safeText(response.getStrengths(), "暂无") + "\n\n" +
                                "待加强：" + safeText(response.getWeaknesses(), "暂无") + "\n\n" +
                                "下一步建议：" + safeText(response.getSuggestions(), "继续保持练习节奏");
        }

        private String safeText(String value, String fallback) {
                return value != null && !value.trim().isEmpty() ? value : fallback;
        }

        /**
         * 根据对练评分更新相关复习任务的掌握度
         */
        private void updateReviewTaskMastery(Long userId, ChatSession session, int score) {
                if (session == null || session.getTopic() == null || session.getTopic().isEmpty()) {
                        log.warn("无法更新掌握度，会话中找不到对练主题");
                        return;
                }

                String topic = session.getTopic();
                String subject = session.getSubject();

                List<ReviewTask> relatedTasks = reviewTaskMapper.selectList(
                                new LambdaQueryWrapper<ReviewTask>()
                                                .eq(ReviewTask::getUserId, userId)
                                                .eq(ReviewTask::getDelFlag, 0)
                                                .and(w -> w.like(ReviewTask::getContent, topic)
                                                                .or().eq(ReviewTask::getSubject, subject)));

                if (relatedTasks.isEmpty()) {
                        log.info("没有找到与主题 '{}' 相关的复习任务，无需更新掌握度", topic);
                        return;
                }

                double newMastery = score / 100.0;
                for (ReviewTask task : relatedTasks) {
                        double oldMastery = task.getMasteryLevel() != null ? task.getMasteryLevel() : 0.0;
                        double updatedMastery = oldMastery * 0.4 + newMastery * 0.6;
                        task.setMasteryLevel(Math.round(updatedMastery * 100.0) / 100.0);
                        task.setReviewCount(task.getReviewCount() != null ? task.getReviewCount() + 1 : 1);
                        reviewTaskMapper.updateById(task);
                }

                log.info("对练结束，更新了{}个复习任务的掌握度，主题：{}，评分：{}", relatedTasks.size(), topic, score);
        }
}
