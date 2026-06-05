package com.geekyan.vo;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class PhraseTranslationVO {
    private String original;
    private String to;
    private String translation;
    private String source;
    private String sourceLabel;
    private List<BilingualItem> bilingual;
    private List<TranslationItem> translations;
    private Object analysis;

    @Data
    public static class BilingualItem {
        private String original;
        private String translation;
    }

    @Data
    public static class TranslationItem {
        private String meaning;
        private ExampleItem example;
        private String sourceLabel;
    }

    @Data
    public static class ExampleItem {
        private String en;
        private String cn;
    }
}
