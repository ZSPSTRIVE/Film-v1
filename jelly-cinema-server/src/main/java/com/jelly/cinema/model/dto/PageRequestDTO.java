package com.jelly.cinema.model.dto;

import lombok.Data;

@Data
public class PageRequestDTO {
    private Integer page = 1;
    private Integer pageSize = 12;
}
