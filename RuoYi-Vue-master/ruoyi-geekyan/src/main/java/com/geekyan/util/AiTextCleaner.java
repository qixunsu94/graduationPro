package com.geekyan.util;

public final class AiTextCleaner {
    private AiTextCleaner() {
    }

    public static String clean(String value) {
        if (value == null) {
            return null;
        }

        return value
                .replace("\r\n", "\n")
                .replaceAll("(?m)^\\s*```[a-zA-Z0-9_-]*\\s*$", "")
                .replace("```", "")
                .replaceAll("(?m)^[ \\t]{0,3}#{1,6}[ \\t]*", "")
                .replaceAll("(?m)^[ \\t]*>[ \\t]?", "")
                .replaceAll("(?m)^[ \\t]*[*+][ \\t]+", "")
                .replaceAll("\\*\\*([^*\\n]+)\\*\\*", "$1")
                .replaceAll("__([^_\\n]+)__", "$1")
                .replaceAll("\\*([^*\\n]+)\\*", "$1")
                .replaceAll("_([^_\\n]+)_", "$1")
                .replaceAll("`([^`\\n]+)`", "$1")
                .replace("`", "")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
