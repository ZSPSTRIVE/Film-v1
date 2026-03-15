package com.jelly.cinema.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jelly.cinema.model.dto.CreateOrderDto;
import com.jelly.cinema.model.entity.Order;
import com.jelly.cinema.model.vo.OrderVo;

public interface OrderService extends IService<Order> {

    /**
     * 提交锁座订单
     */
    String createOrder(Long userId, CreateOrderDto dto);

    /**
     * 根据单号查询订单详情
     */
    OrderVo getOrderDetail(String orderNo, Long userId);

    /**
     * 模拟支付成功
     */
    void payMock(String orderNo, Long userId);
}
