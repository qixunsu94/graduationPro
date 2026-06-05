package com.geekyan.service;

import com.geekyan.service.IAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 学科解析器 - 根据用户输入的对练主题自动识别学科、角色
 * 优先关键词匹配，匹配失败时调用AI兜底分类
 */
@Component
public class SubjectResolver {

    private static final Logger log = LoggerFactory.getLogger(SubjectResolver.class);

    @Autowired
    private IAiService aiService;

    public enum Subject {
        MATH("math", "数学", "高数辅导专家"),
        DATA_STRUCTURE("ds", "数据结构", "数据结构辅导专家"),
        COMPUTER_ORGANIZATION("co", "计算机组成原理", "组成原理辅导专家"),
        OS("os", "操作系统", "操作系统辅导专家"),
        NETWORK("cn", "计算机网络", "计算机网络辅导专家"),
        ENGLISH("english", "英语", "英语私教"),
        GENERAL("general", "通用", "通用AI助手");

        private final String code;
        private final String label;
        private final String role;

        Subject(String code, String label, String role) {
            this.code = code;
            this.label = label;
            this.role = role;
        }

        public String getCode() { return code; }
        public String getLabel() { return label; }
        public String getRole() { return role; }

        public static Subject fromCode(String code) {
            if (code == null) return GENERAL;
            for (Subject s : values()) {
                if (s.code.equalsIgnoreCase(code.trim())) return s;
            }
            return GENERAL;
        }
    }

    // 关键词 -> 学科映射（更具体的关键词放前面）
    private static final LinkedHashMap<String, Subject> KEYWORD_MAP = new LinkedHashMap<>();
    static {
        // 数学
        String[] mathKeywords = {"极限", "导数", "微分", "积分", "级数", "中值定理", "洛必达",
                "泰勒", "行列式", "矩阵", "特征值", "概率", "数理统计", "线性代数",
                "高数", "微积分", "多元函数", "偏导数", "重积分", "曲线积分", "曲面积分",
                "正交", "二次型", "随机变量", "期望", "方差", "大数定律", "中心极限定理"};
        for (String kw : mathKeywords) KEYWORD_MAP.put(kw, Subject.MATH);

        // 数据结构
        String[] dsKeywords = {"二叉树", "红黑树", "AVL", "B树", "B+树", "哈夫曼",
                "链表", "栈", "队列", "排序算法", "查找算法", "哈希表", "散列表",
                "图论", "最短路径", "最小生成树", "拓扑排序", "关键路径",
                "BFS", "DFS", "Dijkstra", "Prim", "Kruskal", "Floyd",
                "快排", "归并排序", "堆排序", "希尔排序", "冒泡排序",
                "折半查找", "KMP", "贪心", "动态规划", "分治",
                "时间复杂度", "空间复杂度", "递归", "遍历"};
        for (String kw : dsKeywords) KEYWORD_MAP.put(kw, Subject.DATA_STRUCTURE);

        // 组成原理
        String[] coKeywords = {"Cache", "缓存", "流水线", "指令系统", "CPU", "ALU",
                "总线", "存储器", "MMU", "TLB", "虚拟存储", "浮点数",
                "补码", "原码", "移码", "中断", "DMA", "寻址方式",
                "指令流水", "数据通路", "控制器", "微程序", "硬布线",
                "SRAM", "DRAM", "EPROM", "磁盘", "RAID", "I/O"};
        for (String kw : coKeywords) KEYWORD_MAP.put(kw, Subject.COMPUTER_ORGANIZATION);

        // 操作系统
        String[] osKeywords = {"进程", "线程", "死锁", "虚拟内存", "页面置换",
                "文件系统", "调度算法", "同步", "互斥", "信号量", "缺页",
                "PV操作", "管程", "银行家算法", "作业调度", "进程通信",
                "内存管理", "分页", "分段", "段页式", "抖动", "磁盘调度",
                "FCFS", "SJF", "优先级调度", "时间片", "临界区"};
        for (String kw : osKeywords) KEYWORD_MAP.put(kw, Subject.OS);

        // 计算机网络
        String[] cnKeywords = {"TCP", "UDP", "IP地址", "路由", "子网", "子网掩码",
                "HTTP", "HTTPS", "DNS", "DHCP", "ARP", "ICMP",
                "拥塞控制", "CSMA", "OSPF", "BGP", "NAT",
                "三次握手", "四次挥手", "滑动窗口", "MAC地址",
                "以太网", "数据链路层", "网络层", "传输层", "应用层",
                "物理层", "协议栈", "Socket", "端口"};
        for (String kw : cnKeywords) KEYWORD_MAP.put(kw, Subject.NETWORK);

        // 英语
        String[] enKeywords = {"英语", "单词", "语法", "长难句", "阅读理解",
                "翻译技巧", "口语", "写作", "完形填空", "定语从句",
                "虚拟语气", "倒装句", "非谓语", "时态", "语态",
                "从句", "主谓一致", "冠词", "介词", "连词"};
        for (String kw : enKeywords) KEYWORD_MAP.put(kw, Subject.ENGLISH);
    }

    /**
     * 解析主题，自动识别学科
     */
    public Subject resolveSubject(String topic) {
        if (topic == null || topic.trim().isEmpty()) {
            return Subject.GENERAL;
        }

        // 1. 先关键词匹配
        Subject matched = resolveByKeywords(topic);
        if (matched != null) {
            log.info("关键词匹配成功: topic={}, subject={}", topic, matched.getCode());
            return matched;
        }

        // 2. 关键词匹配失败，调用AI分类
        Subject aiResult = resolveByAI(topic);
        log.info("AI分类结果: topic={}, subject={}", topic, aiResult.getCode());
        return aiResult;
    }

    /**
     * 关键词匹配
     */
    private Subject resolveByKeywords(String topic) {
        String lower = topic.toLowerCase();
        for (Map.Entry<String, Subject> entry : KEYWORD_MAP.entrySet()) {
            if (lower.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * AI兜底分类
     */
    private Subject resolveByAI(String topic) {
        try {
            String classifyPrompt = "你是一个学科分类器。用户输入了一个对练主题，请判断它属于哪个学科。\n" +
                    "可选学科代码：math, ds, co, os, cn, english, general\n" +
                    "如果无法明确判断，返回 general。\n" +
                    "请只返回学科代码，不要输出其他任何内容。";
            String userMessage = "对练主题：" + topic;
            String result = aiService.chatWithoutHistory(classifyPrompt, userMessage);
            if (result == null || result.trim().isEmpty()) {
                return Subject.GENERAL;
            }
            String code = result.trim().toLowerCase();
            // 清理可能的多余字符
            if (code.contains("\n")) code = code.split("\n")[0].trim();
            if (code.contains(" ")) code = code.split(" ")[0].trim();
            return Subject.fromCode(code);
        } catch (Exception e) {
            log.warn("AI学科分类失败，使用默认值: topic={}, error={}", topic, e.getMessage());
            return Subject.GENERAL;
        }
    }
}
