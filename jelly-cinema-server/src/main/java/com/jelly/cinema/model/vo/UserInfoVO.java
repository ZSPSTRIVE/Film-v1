package com.jelly.cinema.model.vo;

import lombok.Data;

@Data
public class UserInfoVO {
    private Long id;
    private String username;
    private String phone;
    private String email;
    private String role;
    private String avatar; // Normally comes from user_profile, for now we keep it simple
}
