package com.geekyan.vo;

import java.io.Serializable;

/**
 * AI对练课后评估报告
 */
public class PracticeEndResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 综合评分 0-100 */
    private Integer score;

    /** 优点 */
    private String strengths;

    /** 薄弱点 */
    private String weaknesses;

    /** 建议 */
    private String suggestions;

    /** 对练主题 */
    private String topic;

    /** 对练轮次（用户回答次数） */
    private Integer rounds;

    public PracticeEndResponse() {}

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }
    public String getWeaknesses() { return weaknesses; }
    public void setWeaknesses(String weaknesses) { this.weaknesses = weaknesses; }
    public String getSuggestions() { return suggestions; }
    public void setSuggestions(String suggestions) { this.suggestions = suggestions; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public Integer getRounds() { return rounds; }
    public void setRounds(Integer rounds) { this.rounds = rounds; }
}
