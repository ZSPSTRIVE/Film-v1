package com.jelly.cinema.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CinemaVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String phone;
    
    /**
     * 服务标签，如 "退", "改签", "3D眼镜"
     */
    private String servicesJson;
    
    // 如果有距离计算，这里可以补充一个距离字段
    private Double distance;
}
