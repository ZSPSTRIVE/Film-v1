package com.jelly.cinema.controller.app;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.jelly.cinema.common.R;
import com.jelly.cinema.model.entity.User;
import com.jelly.cinema.model.vo.UserVo;
import com.jelly.cinema.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "App-用户资料模块")
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取当前上下文用户资料")
    @SaCheckLogin
    @GetMapping("/profile")
    public R<UserVo> getProfile() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(userId);
        
        UserVo vo = new UserVo();
        BeanUtil.copyProperties(user, vo);
        return R.ok(vo);
    }
}
