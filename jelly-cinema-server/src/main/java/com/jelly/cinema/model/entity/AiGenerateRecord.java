package com.jelly.cinema.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("ai_generate_record")
public class AiGenerateRecord implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long adminUserId;

    private String sceneCode;

    private String originalContent;

    private String generatedContent;

    private Integer tokensUsed;

    @TableField("create_time")
    private LocalDateTime createTime;
}
