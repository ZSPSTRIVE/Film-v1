package com.jelly.cinema.model.dto;

import com.jelly.cinema.model.dto.ai.AiSearchRequestDTO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectIllegalMediaSearchRequest() {
        MediaSearchDTO dto = new MediaSearchDTO();
        dto.setPage(0);
        dto.setPageSize(99);
        dto.setKeyword("a".repeat(61));
        dto.setType(9);
        dto.setStatus(-1);

        var violations = validator.validate(dto);

        assertThat(violations)
                .extracting(violation -> violation.getMessage())
                .contains(
                        "页码最小为 1",
                        "每页条数最大为 24",
                        "搜索关键词长度不能超过 60 个字符",
                        "影视类型取值非法",
                        "影视状态取值非法"
                );
    }

    @Test
    void shouldAcceptValidAiSearchRequest() {
        AiSearchRequestDTO dto = new AiSearchRequestDTO();
        dto.setQuery("钢铁侠");
        dto.setPage(1);
        dto.setPageSize(12);

        var violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }
}
