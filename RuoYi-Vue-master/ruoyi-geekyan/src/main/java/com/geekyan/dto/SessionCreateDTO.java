package com.geekyan.dto;

import lombok.Data;

@Data
public class SessionCreateDTO {
    private String name;
    private String subject;
    private String role;
    private String topic;
    private String sessionType;
}
