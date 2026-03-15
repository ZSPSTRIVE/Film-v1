package com.jelly.cinema.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("media")
public class Media implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;

    private String originalTitle;

    private Integer type; // 1: Movie, 2: TV, 3: Anime

    private Integer status; // 0: Prep, 1: Upcoming, 2: Showing, 3: Off

    private LocalDate releaseDate;

    private Integer duration;

    private String coverUrl;

    private String backdropUrl;

    private String summary;

    private BigDecimal rating;

    private String trailerUrl;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
