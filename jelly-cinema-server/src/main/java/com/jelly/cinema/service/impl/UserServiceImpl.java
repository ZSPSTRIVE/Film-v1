package com.jelly.cinema.service.impl;

import cn.dev33.satoken.secure.SaSecureUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jelly.cinema.common.exception.BusinessException;
import com.jelly.cinema.mapper.UserMapper;
import com.jelly.cinema.model.dto.LoginDTO;
import com.jelly.cinema.model.dto.RegisterDTO;
import com.jelly.cinema.model.entity.User;
import com.jelly.cinema.model.vo.UserInfoVO;
import com.jelly.cinema.service.UserService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    // Simple salt for demonstration. In production, load from properties.
    private static final String PWD_SALT = "jelly_cinema_secret_salt_2026";

    @Override
    public String login(LoginDTO loginDTO) {
        String encryptPassword = SaSecureUtil.sha256(loginDTO.getPassword() + PWD_SALT);
        
        User user = lambdaQuery()
                .eq(User::getUsername, loginDTO.getUsername())
                .eq(User::getPassword, encryptPassword)
                .one();
        
        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(403, "当前账号已被封禁");
        }

        // Login using Sa-Token
        StpUtil.login(user.getId());
        
        // Return token value
        return StpUtil.getTokenValue();
    }

    @Override
    public void register(RegisterDTO registerDTO) {
        Long count = lambdaQuery().eq(User::getUsername, registerDTO.getUsername()).count();
        if (count > 0) {
            throw new BusinessException(400, "用户名已存在");
        }

        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(SaSecureUtil.sha256(registerDTO.getPassword() + PWD_SALT));
        user.setPhone(registerDTO.getPhone());
        user.setEmail(registerDTO.getEmail());
        user.setRole("USER");
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        
        this.save(user);
    }

    @Override
    public UserInfoVO getUserInfo(Long userId) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        // For actual avatar, you'd join with user_profile table. We mock it for now.
        vo.setAvatar("https://api.dicebear.com/7.x/notionists/svg?seed=" + user.getUsername());
        return vo;
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }
}
