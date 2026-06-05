package com.geekyan.dto;

import lombok.Data;

@Data
public class ReverseSearchDTO {
    private String q;
    private int limit = 20;
}
