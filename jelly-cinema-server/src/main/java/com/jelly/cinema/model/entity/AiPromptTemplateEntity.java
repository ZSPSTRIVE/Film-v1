package com.jelly.cinema.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@TableName("ai_prompt_template")
public class AiPromptTemplateEntity implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String sceneCode;

    private String templateContent;

    private String modelName;

    private BigDecimal temperature;
}
