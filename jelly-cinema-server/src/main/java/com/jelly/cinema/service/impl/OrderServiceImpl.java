package com.jelly.cinema.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jelly.cinema.mapper.OrderMapper;
import com.jelly.cinema.model.dto.CreateOrderDto;
import com.jelly.cinema.model.entity.Order;
import com.jelly.cinema.model.vo.OrderVo;
import com.jelly.cinema.service.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(Long userId, CreateOrderDto dto) {
        // 核心锁定逻辑（此处可结合Redis做分布式锁、库存防重校验）
        // 1. 验证场次 scheduleId 是否有效
        // 2. 验证席位 seatIds 是否可用
        // 3. 产生订单编号
        String orderNo = IdUtil.getSnowflakeNextIdStr();
        
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setScheduleId(dto.getScheduleId());
        
        // 模拟算价逻辑：假定每个座位 45.00 元
        BigDecimal seatPrice = new BigDecimal("45.00");
        BigDecimal totalPrice = seatPrice.multiply(new BigDecimal(dto.getSeatIds().size()));
        
        order.setTotalPrice(totalPrice);
        order.setPayPrice(totalPrice); // 模拟原价支付，无优惠券
        
        // 订单状态 - 0: 待支付
        order.setStatus(0);
        
        // 生成过期倒计时：下单后15分钟未支付则自动取消释放座位
        order.setExpireTime(LocalDateTime.now().plusMinutes(15));
        
        this.save(order);
        
        // 4. (预留) TODO: 批量保存 OrderTicket 锁定记录 (seat_row, seat_col)
        return orderNo;
    }

    @Override
    public OrderVo getOrderDetail(String orderNo, Long userId) {
        if (StrUtil.isBlank(orderNo)) {
            throw new RuntimeException("订单号不能为空");
        }
    
        Order order = this.getOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId));
                
        if (order == null) {
            throw new RuntimeException("订单不存在或无权访问");
        }
        
        OrderVo vo = new OrderVo();
        BeanUtil.copyProperties(order, vo);
        
        // 模拟联表补全名称数据 TODO: 结合 ScheduleService 和 MediaService 聚合数据
        vo.setMediaTitle("已选影片名称");
        vo.setCinemaName("已选影院名称");
        vo.setHallName("X号激光厅");
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payMock(String orderNo, Long userId) {
        Order order = this.getOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId));
                
        if (order == null || order.getStatus() != 0) {
            throw new RuntimeException("订单状态异常或不存在");
        }
        
        // 交易履约成功，更新状态为 1已支付
        order.setStatus(1);
        order.setPayTime(LocalDateTime.now());
        
        this.updateById(order);
    }
}
