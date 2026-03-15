package com.jelly.cinema.controller.app;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.jelly.cinema.common.R;
import com.jelly.cinema.model.dto.CreateOrderDto;
import com.jelly.cinema.model.vo.OrderVo;
import com.jelly.cinema.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "App-订单交易模块")
@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "提交锁座订单")
    @SaCheckLogin
    @PostMapping("/create")
    public R<String> createOrder(@Valid @RequestBody CreateOrderDto dto) {
        long userId = StpUtil.getLoginIdAsLong();
        String orderNo = orderService.createOrder(userId, dto);
        return R.ok(orderNo);
    }

    @Operation(summary = "查询订单详情")
    @SaCheckLogin
    @GetMapping("/{orderNo}")
    public R<OrderVo> getOrderDetail(@PathVariable String orderNo) {
        long userId = StpUtil.getLoginIdAsLong();
        OrderVo vo = orderService.getOrderDetail(orderNo, userId);
        return R.ok(vo);
    }

    @Operation(summary = "测试环境模拟支付接口")
    @SaCheckLogin
    @PostMapping("/pay")
    public R<Void> payMock(@RequestParam String orderNo) {
        long userId = StpUtil.getLoginIdAsLong();
        orderService.payMock(orderNo, userId);
        return R.ok(null, "支付成功");
    }
}
