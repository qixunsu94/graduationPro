package com.geekyan.dto;

import lombok.Data;

@Data
public class PhraseTranslationDTO {
    private String text;
    private String to = "zh";
}
