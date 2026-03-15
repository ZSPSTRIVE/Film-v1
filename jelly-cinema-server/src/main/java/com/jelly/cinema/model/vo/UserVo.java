package com.jelly.cinema.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String phone;
    private String email;
    private String role;
    private LocalDateTime createTime;
}
