package com.jelly.cinema.service.ai;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AiSearchQueryHelper {

    private static final String[] RECOMMENDATION_VERBS = {
            "推荐", "来点", "想看", "适合", "有没有", "求一部", "找一部"
    };

    private static final String[] MOOD_TERMS = {
            "放松", "轻松", "治愈", "解压", "下班", "搞笑", "喜剧", "悬疑", "惊悚", "推理", "犯罪", "节奏快", "烧脑"
    };

    private static final String[] FILTER_TERMS = {
            "最近", "近年", "今年", "去年", "高分", "口碑", "评分", "热映", "上映", "待上映", "仅限", "状态", "类型"
    };

    private static final String[] TOKEN_HINTS = {
            "动画", "动漫", "电影", "电视剧", "剧集", "热映", "高分", "口碑", "最近",
            "放松", "轻松", "治愈", "解压", "喜剧", "悬疑", "惊悚", "推理", "犯罪", "节奏快", "烧脑", "下班"
    };

    private AiSearchQueryHelper() {
    }

    public static AiQueryMode classify(String rawQuery, String normalizedQuery) {
        String raw = defaultText(rawQuery, "").trim();
        String normalized = defaultText(normalizedQuery, "").trim();
        String combined = (raw + " " + normalized).toLowerCase(Locale.ROOT);

        boolean hasRecommendationVerb = containsAny(combined, RECOMMENDATION_VERBS);
        boolean hasMoodTerm = containsAny(combined, MOOD_TERMS);
        boolean hasFilterTerm = containsAny(combined, FILTER_TERMS);
        boolean looksLikeTitle = !hasRecommendationVerb
                && !hasFilterTerm
                && raw.length() > 0
                && raw.length() <= 12
                && !raw.contains("，")
                && !raw.contains(",")
                && !raw.contains(" ")
                && !raw.contains("的");

        if (hasRecommendationVerb || hasMoodTerm) {
            return AiQueryMode.RECOMMENDATION;
        }
        if (looksLikeTitle) {
            return AiQueryMode.TITLE;
        }
        return AiQueryMode.FILTERED_DISCOVERY;
    }

    public static List<String> extractSoftTerms(String rawQuery, String normalizedQuery) {
        Set<String> terms = new LinkedHashSet<>();
        String raw = defaultText(rawQuery, "").trim();
        String normalized = defaultText(normalizedQuery, "").trim();
        if (StringUtils.hasText(normalized) && normalized.length() <= 16) {
            terms.add(normalized.toLowerCase(Locale.ROOT));
        }

        String combined = (raw + " " + normalized).toLowerCase(Locale.ROOT);
        for (String hint : TOKEN_HINTS) {
            if (combined.contains(hint)) {
                terms.add(hint);
            }
        }

        for (String token : combined.replace('，', ' ').replace(',', ' ').split("\\s+")) {
            if (StringUtils.hasText(token) && token.length() >= 2) {
                terms.add(token);
            }
        }
        return new ArrayList<>(terms);
    }

    public static boolean containsAny(String text, String... terms) {
        if (!StringUtils.hasText(text) || terms == null || terms.length == 0) {
            return false;
        }
        for (String term : terms) {
            if (StringUtils.hasText(term) && text.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
