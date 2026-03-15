package com.jelly.cinema.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("media_cast")
public class MediaCast implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long mediaId;
    private Long actorId;
    private Integer roleType; // 1: Director, 2: Writer, 3: Lead Actor, 4: Supporting
    private String characterName;
}
