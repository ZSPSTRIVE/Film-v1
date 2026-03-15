package com.jelly.cinema.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MediaVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String originalTitle;
    private Integer type;
    private Integer status;
    private LocalDate releaseDate;
    private Integer duration;
    private String coverUrl;
    private String backdropUrl;
    private String summary;
    private BigDecimal rating;
    private String trailerUrl;
}
