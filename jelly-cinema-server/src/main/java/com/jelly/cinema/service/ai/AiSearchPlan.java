package com.jelly.cinema.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiSearchPlan {

    private String normalizedQuery;

    private String semanticQuery;

    private String intentSummary;

    private Integer type;

    private Integer status;

    private String sortBy;

    private String queryMode;

    private Double minRating;

    private Integer yearFrom;

    private Integer yearTo;

    private Integer page;

    private Integer pageSize;
}
