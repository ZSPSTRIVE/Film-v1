package com.jelly.cinema.model.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiCommentAuditRequest {

    private Long mediaId;

    private Long commentId;

    @NotBlank(message = "评论内容不能为空")
    private String content;
}
