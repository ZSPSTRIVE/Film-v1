package com.jelly.cinema.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.jelly.cinema.common.result.R;
import com.jelly.cinema.model.dto.LoginDTO;
import com.jelly.cinema.model.dto.RegisterDTO;
import com.jelly.cinema.model.vo.UserInfoVO;
import com.jelly.cinema.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/login")
    public R<String> login(@Valid @RequestBody LoginDTO loginDTO) {
        String token = userService.login(loginDTO);
        return R.ok(token);
    }

    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterDTO registerDTO) {
        userService.register(registerDTO);
        return R.ok();
    }

    @GetMapping("/info")
    @SaCheckLogin
    public R<UserInfoVO> getUserInfo() {
        Long userId = StpUtil.getLoginIdAsLong();
        UserInfoVO userInfo = userService.getUserInfo(userId);
        return R.ok(userInfo);
    }

    @PostMapping("/logout")
    @SaCheckLogin
    public R<Void> logout() {
        userService.logout();
        return R.ok();
    }
}
