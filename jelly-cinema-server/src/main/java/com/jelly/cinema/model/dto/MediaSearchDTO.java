package com.jelly.cinema.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MediaSearchDTO extends PageRequestDTO {
    private String keyword;
    private Integer type;
    private Integer status;
}
