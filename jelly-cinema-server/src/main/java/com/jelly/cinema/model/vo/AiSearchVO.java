package com.jelly.cinema.model.vo;

import com.jelly.cinema.model.entity.Media;
import lombok.Data;

import java.util.List;

@Data
public class AiSearchVO {
    private String answer;
    private List<Media> mediaList;
    private String normalizedQuery;
    private String intentSummary;
    private String retrievalMode;
    private Integer matchedCount;
    private List<AiCitationVO> citations;
}
