package com.geekyan.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SessionVO {
    private Long id;
    private String sessionId;
    private String name;
    private String subject;
    private String role;
    private String topic;
    private String sessionType;
    private Integer messageCount;
    private LocalDateTime lastMessageTime;
    private LocalDateTime createTime;
}
