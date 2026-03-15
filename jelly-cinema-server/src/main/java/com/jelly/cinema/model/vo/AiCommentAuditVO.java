package com.jelly.cinema.model.vo;

import lombok.Data;

@Data
public class AiCommentAuditVO {

    private Boolean pass;

    private Integer riskLevel;

    private String suggestion;

    private String reason;
}
