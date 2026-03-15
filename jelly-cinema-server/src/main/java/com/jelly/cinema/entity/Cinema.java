package com.jelly.cinema.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cinema")
public class Cinema {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer brandId;

    private String province;

    private String city;

    private String district;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String phone;

    private String servicesJson;
}
