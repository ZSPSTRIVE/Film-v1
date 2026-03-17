package com.jelly.cinema.model.vo;

import lombok.Data;

@Data
public class AiCitationVO {

    private Long mediaId;

    private String title;

    private String snippet;

    private String source;

    private String knowledgeBaseCode;

    private Double score;
}
