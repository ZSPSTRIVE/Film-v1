package com.jelly.cinema.dto;

import lombok.Data;

@Data
public class MediaSearchRequest {
    private String query;
    private Integer type;
    private Integer status;
    private Integer page = 1;
    private Integer pageSize = 20;
}
