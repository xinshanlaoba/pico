package com.picojava.common;

public final class TextUtils {
    private TextUtils() {}

    public static String clip(String text, int limit) {
        String value = text == null ? "" : text;
        if (value.length() <= limit) return value;
        return value.substring(0, limit) + "\n...[已截断 " + (value.length() - limit) + " 个字符]";
    }

    public static String middle(String text, int limit) {
        String value = String.valueOf(text).replace("\n", " ");
        if (value.length() <= limit) return value;
        if (limit <= 3) return value.substring(0, limit);
        int left = (limit - 3) / 2;
        int right = limit - 3 - left;
        return value.substring(0, left) + "..." + value.substring(value.length() - right);
    }
}
