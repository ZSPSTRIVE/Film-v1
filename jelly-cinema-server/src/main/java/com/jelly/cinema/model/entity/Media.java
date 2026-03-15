package com.jelly.cinema.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 泛影视内容主表
 */
@Data
@TableName("media")
public class Media implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String originalTitle;

    /**
     * 类型: 1电影 2电视剧 3动漫
     */
    private Integer type;

    /**
     * 状态: 0筹备 1待映 2热映 3下架
     */
    private Integer status;

    private LocalDate releaseDate;

    private Integer duration;

    private String coverUrl;

    private String backdropUrl;

    private String summary;

    private BigDecimal rating;

    private String trailerUrl;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
