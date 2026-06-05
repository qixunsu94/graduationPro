package com.geekyan.vo;

import lombok.Data;
import java.util.List;

@Data
public class ReverseSearchVO {
    private String query;
    private List<ResultItem> results;
    private List<ExampleItem> examples;
    private String source;
    private String sourceLabel;

    @Data
    public static class ResultItem {
        private String match;
        private String word;
        private String definition;
        private String phonetic;
        private String pos;
        private String source;
    }

    @Data
    public static class ExampleItem {
        private String en;
        private String cn;
    }
}
