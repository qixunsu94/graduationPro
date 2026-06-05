package com.geekyan.vo;

import java.io.Serializable;

/**
 * AI对练开始响应的视图对象
 */
public class PracticeStartResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 新创建的对练会话ID */
    private String sessionId;

    /** AI提出的第一个问题 */
    private String firstQuestion;

    /** 解析后的学科代码 */
    private String subject;

    /** 解析后的角色名称 */
    private String role;

    /** 学科中文名 */
    private String subjectLabel;

    public PracticeStartResponse() {
    }

    public PracticeStartResponse(String sessionId, String firstQuestion) {
        this.sessionId = sessionId;
        this.firstQuestion = firstQuestion;
    }

    public PracticeStartResponse(String sessionId, String firstQuestion, String subject, String role,
            String subjectLabel) {
        this.sessionId = sessionId;
        this.firstQuestion = firstQuestion;
        this.subject = subject;
        this.role = role;
        this.subjectLabel = subjectLabel;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getFirstQuestion() {
        return firstQuestion;
    }

    public void setFirstQuestion(String firstQuestion) {
        this.firstQuestion = firstQuestion;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getSubjectLabel() {
        return subjectLabel;
    }

    public void setSubjectLabel(String subjectLabel) {
        this.subjectLabel = subjectLabel;
    }
}
