package com.jelly.cinema.model.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiSearchRequestDTO {

    @NotBlank(message = "搜索词不能为空")
    @Size(max = 60, message = "搜索词长度不能超过 60 个字符")
    private String query;

    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 24, message = "每页条数最大为 24")
    private Integer pageSize = 12;
}
