package com.jelly.cinema.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("actor")
public class Actor {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String foreignName;

    private String avatarUrl;

    private String bio;
}
