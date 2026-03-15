package com.jelly.cinema.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("media")
public class Media {
    @TableId(type = IdType.AUTO)
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
