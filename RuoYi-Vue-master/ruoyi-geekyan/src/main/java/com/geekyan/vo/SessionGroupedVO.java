package com.geekyan.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SessionGroupedVO {
    private List<SubjectGroup> groups;

    @Data
    public static class SubjectGroup {
        private String subject;
        private String subjectLabel;
        private int count;
        private List<SessionItem> sessions;
    }

    @Data
    public static class SessionItem {
        private Long id;
        private String sessionId;
        private String name;
        private String subject;
        private String subjectLabel;
        private String sessionType;
        private Integer messageCount;
        private LocalDateTime lastMessageTime;
        private LocalDateTime createTime;
    }
}
