package com.jelly.cinema.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderDto {

    @NotNull(message = "排期场次ID不能为空")
    private Long scheduleId;

    @NotEmpty(message = "必须选择至少一个座位")
    private List<Long> seatIds;
    
    // 可能还有优惠券ID等字段
}
