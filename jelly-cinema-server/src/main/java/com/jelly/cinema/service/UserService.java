package com.jelly.cinema.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jelly.cinema.model.dto.LoginDTO;
import com.jelly.cinema.model.dto.RegisterDTO;
import com.jelly.cinema.model.entity.User;
import com.jelly.cinema.model.vo.UserInfoVO;

public interface UserService extends IService<User> {

    String login(LoginDTO loginDTO);

    void register(RegisterDTO registerDTO);

    UserInfoVO getUserInfo(Long userId);
    
    void logout();
}
