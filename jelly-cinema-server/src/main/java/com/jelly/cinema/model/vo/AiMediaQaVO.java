package com.jelly.cinema.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class AiMediaQaVO {

    private String conversationId;

    private String answer;

    private List<String> references;
}
