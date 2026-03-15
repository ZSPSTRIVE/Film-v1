package com.jelly.cinema.vo;

import lombok.Data;

@Data
public class ActorVO {
    private Long id;
    private String name;
    private String foreignName;
    private String avatarUrl;
    private String bio;
    private Integer roleType;
    private String characterName;
}
