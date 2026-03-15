package com.jelly.cinema.dto;

import lombok.Data;

@Data
public class MediaSummaryRequest {
    private Long mediaId;
    private String originalSummary;
}
