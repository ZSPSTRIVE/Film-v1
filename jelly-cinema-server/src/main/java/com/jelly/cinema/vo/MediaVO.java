package com.jelly.cinema.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class MediaVO {
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
    private List<ActorVO> actors;
    private List<String> categories;
    private Integer commentCount;
    private BigDecimal avgRating;
}
