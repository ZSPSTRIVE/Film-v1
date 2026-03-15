package com.jelly.cinema.model.vo;

import com.jelly.cinema.model.entity.Media;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class MediaDetailVO extends Media {
    private List<ActorVO> actors;
    private Integer commentCount;
    
    @Data
    public static class ActorVO {
        private Long id;
        private String name;
        private String avatarUrl;
        private Integer roleType;
        private String characterName;
    }
}
