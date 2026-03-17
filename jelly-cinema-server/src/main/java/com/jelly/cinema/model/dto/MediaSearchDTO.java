package com.jelly.cinema.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MediaSearchDTO extends PageRequestDTO {
    @Size(max = 60, message = "搜索关键词长度不能超过 60 个字符")
    private String keyword;

    @Min(value = 0, message = "影视类型取值非法")
    @Max(value = 3, message = "影视类型取值非法")
    private Integer type;

    @Min(value = 0, message = "影视状态取值非法")
    @Max(value = 3, message = "影视状态取值非法")
    private Integer status;
}
