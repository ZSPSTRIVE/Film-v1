package com.jelly.cinema.service.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiSearchQueryHelperTest {

    @Test
    void shouldClassifyShortMovieTitleAsTitleQuery() {
        AiQueryMode mode = AiSearchQueryHelper.classify("钢铁侠", "钢铁侠");

        assertThat(mode).isEqualTo(AiQueryMode.TITLE);
    }

    @Test
    void shouldClassifyRecommendationPromptAsRecommendationQuery() {
        AiQueryMode mode = AiSearchQueryHelper.classify("推荐一部下班后轻松一点的电影", "轻松");

        assertThat(mode).isEqualTo(AiQueryMode.RECOMMENDATION);
    }

    @Test
    void shouldExtractSoftTermsWithoutLosingCoreKeywords() {
        List<String> terms = AiSearchQueryHelper.extractSoftTerms("最近高分动画", "动画");

        assertThat(terms).contains("动画", "最近", "高分");
    }
}
