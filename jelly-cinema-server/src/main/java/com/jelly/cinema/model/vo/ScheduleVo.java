package com.jelly.cinema.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ScheduleVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long mediaId;
    private Long cinemaId;
    private Long hallId;
    
    private LocalDateTime showTime;
    private LocalDateTime endTime;
    private BigDecimal price;
    
    // 可以聚合影厅信息
    private String hallName;
    private String hallType;
}
