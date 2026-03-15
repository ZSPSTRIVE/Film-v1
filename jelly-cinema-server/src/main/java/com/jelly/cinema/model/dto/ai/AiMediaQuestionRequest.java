package com.jelly.cinema.model.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiMediaQuestionRequest {

    @NotNull(message = "影视 ID 不能为空")
    private Long mediaId;

    @NotBlank(message = "问题不能为空")
    private String question;

    private String conversationId;
}
