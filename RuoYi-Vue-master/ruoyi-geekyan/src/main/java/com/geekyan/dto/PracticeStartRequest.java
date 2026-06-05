package com.geekyan.dto;

import java.io.Serializable;

/**
 * AI对练开始请求的数据传输对象
 */
public class PracticeStartRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 对练的主题，例如 "二叉树", "新民主主义革命理论" */
    private String topic;

    /** 对练的学科，例如 "ds", "math" */
    private String subject;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }
}
