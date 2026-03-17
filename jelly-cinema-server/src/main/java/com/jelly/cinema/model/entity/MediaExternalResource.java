package com.jelly.cinema.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("media_external_resource")
public class MediaExternalResource implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long mediaId;

    private String providerName;

    private String externalItemId;

    private String rawTitle;

    private String cleanTitle;

    private Integer releaseYear;

    private Integer type;

    private BigDecimal rating;

    private String region;

    private String director;

    private String actors;

    private String description;

    private String coverUrl;

    private String sourceKey;

    private String rawPayloadJson;

    private BigDecimal matchConfidence;

    private String syncStatus;

    private LocalDateTime lastSyncedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
