package com.jelly.cinema.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("media_play_source")
public class MediaPlaySource {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long mediaId;

    private String sourceType;

    private String providerName;

    private String title;

    private String url;

    private String region;

    private String quality;

    private Integer isFree;

    private Integer sortOrder;

    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
