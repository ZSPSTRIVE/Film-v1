package com.jelly.cinema.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jelly.cinema.model.dto.LoginDto;
import com.jelly.cinema.model.dto.RegisterDto;
import com.jelly.cinema.model.entity.User;

public interface UserService extends IService<User> {

    String login(LoginDto loginDto);

    void register(RegisterDto registerDto);

    User getUserByUsername(String username);
}
