package com.jelly.cinema.model.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiBannerCopyRequest {

    private Long mediaId;

    @NotBlank(message = "运营位不能为空")
    private String positionType;

    private String targetAudience;

    private String campaignGoal;

    private String extraRequirement;
}
