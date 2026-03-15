package com.jelly.cinema.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiSearchPlan {

    private String normalizedQuery;

    private String intentSummary;

    private Integer type;

    private Integer status;

    private String sortBy;

    private Integer page;

    private Integer pageSize;
}
