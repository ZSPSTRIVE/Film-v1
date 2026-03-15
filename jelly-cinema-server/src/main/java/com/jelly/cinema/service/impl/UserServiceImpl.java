package com.jelly.cinema.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jelly.cinema.mapper.UserMapper;
import com.jelly.cinema.model.dto.LoginDto;
import com.jelly.cinema.model.dto.RegisterDto;
import com.jelly.cinema.model.entity.User;
import com.jelly.cinema.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public String login(LoginDto loginDto) {
        User user = getUserByUsername(loginDto.getUsername());
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new RuntimeException("该账号已被封禁");
        }
        
        // 兼容明文密码 (测试阶段) 以及 BCrypt 密文密码 (生产规范)
        boolean isMatch = loginDto.getPassword().equals(user.getPassword()) || 
                          BCrypt.checkpw(loginDto.getPassword(), user.getPassword());
                          
        if (!isMatch) {
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 执行登录
        StpUtil.login(user.getId());
        return StpUtil.getTokenValue();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDto registerDto) {
        User existUser = getUserByUsername(registerDto.getUsername());
        if (existUser != null) {
            throw new RuntimeException("用户名已被注册");
        }
        
        User user = new User();
        user.setUsername(registerDto.getUsername());
        // 密码默认使用 BCrypt 加密
        user.setPassword(BCrypt.hashpw(registerDto.getPassword(), BCrypt.gensalt()));
        user.setPhone(registerDto.getPhone());
        user.setEmail(registerDto.getEmail());
        user.setRole("USER");
        user.setStatus(1); // 正常枚举状态
        
        this.save(user);
    }

    @Override
    public User getUserByUsername(String username) {
        return this.getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }
}
