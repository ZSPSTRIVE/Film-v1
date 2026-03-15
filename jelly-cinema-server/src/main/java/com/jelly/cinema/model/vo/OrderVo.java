package com.jelly.cinema.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderNo;
    private Long scheduleId;
    
    // 扩展字段，冗余呈现给前端
    private String mediaTitle;
    private String cinemaName;
    private String hallName;
    private LocalDateTime showTime;
    
    private BigDecimal payPrice;
    private Integer status;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}
