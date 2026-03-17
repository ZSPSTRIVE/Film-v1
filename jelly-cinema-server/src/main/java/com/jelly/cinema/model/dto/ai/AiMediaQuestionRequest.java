package com.jelly.cinema.model.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiMediaQuestionRequest {

    private Long mediaId;

    @NotBlank(message = "问题不能为空")
    private String question;

    private String conversationId;
}
