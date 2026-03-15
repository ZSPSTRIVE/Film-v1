package com.jelly.cinema.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("search_keyword_log")
public class SearchKeywordLog implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String keyword;

    private Long userId;

    @TableField("search_time")
    private LocalDateTime searchTime;
}
