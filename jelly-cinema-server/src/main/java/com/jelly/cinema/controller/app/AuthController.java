package com.jelly.cinema.controller.app;

import cn.dev33.satoken.stp.StpUtil;
import com.jelly.cinema.common.R;
import com.jelly.cinema.model.dto.LoginDto;
import com.jelly.cinema.model.dto.RegisterDto;
import com.jelly.cinema.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "App-认证鉴权模块")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public R<String> login(@Valid @RequestBody LoginDto loginDto) {
        String token = userService.login(loginDto);
        return R.ok(token, "登录成功");
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterDto registerDto) {
        userService.register(registerDto);
        return R.ok(null, "注册成功");
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public R<Void> logout() {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
        return R.ok(null, "已退出登录");
    }
}
