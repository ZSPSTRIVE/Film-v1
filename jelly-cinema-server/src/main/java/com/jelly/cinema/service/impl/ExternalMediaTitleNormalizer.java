package com.jelly.cinema.service.impl;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ExternalMediaTitleNormalizer {

    private static final String[] NOISE_MARKERS = {
            "解说", "预告", "花絮", "发布会", "直播", "抢先看", "速看", "幕后", "访谈"
    };

    private static final Pattern BRACKET_CONTENT = Pattern.compile("[\\[【(（].*?[\\]】)）]");
    private static final Pattern SEASON_SUFFIX = Pattern.compile("(第\\s*[一二三四五六七八九十百0-9]+\\s*季|season\\s*[0-9]+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VERSION_SUFFIX = Pattern.compile("(动画版|動畫版|真人版|全程回顾|特别篇|特別篇|SP)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern TRAILING_PUNCT = Pattern.compile("[·:：\\-—_]+$");

    private ExternalMediaTitleNormalizer() {
    }

    public static String cleanTitle(String rawTitle) {
        if (!StringUtils.hasText(rawTitle)) {
            return "";
        }
        String normalized = rawTitle.trim();
        normalized = BRACKET_CONTENT.matcher(normalized).replaceAll(" ");
        normalized = normalized.replace('：', ':');
        normalized = SEASON_SUFFIX.matcher(normalized).replaceAll("");
        normalized = VERSION_SUFFIX.matcher(normalized).replaceAll("");
        normalized = normalized.replaceAll("[\\u3000]+", " ");
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ").trim();
        normalized = TRAILING_PUNCT.matcher(normalized).replaceAll("").trim();
        return normalized;
    }

    public static boolean isDerivativeNoise(String rawTitle) {
        if (!StringUtils.hasText(rawTitle)) {
            return true;
        }
        String value = rawTitle.toLowerCase(Locale.ROOT);
        for (String marker : NOISE_MARKERS) {
            if (value.contains(marker.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
