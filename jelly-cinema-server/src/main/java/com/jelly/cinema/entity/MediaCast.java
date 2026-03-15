package com.jelly.cinema.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("media_cast")
public class MediaCast {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mediaId;

    private Long actorId;

    private Integer roleType;

    private String characterName;
}
