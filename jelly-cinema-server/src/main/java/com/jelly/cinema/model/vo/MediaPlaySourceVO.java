package com.jelly.cinema.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MediaPlaySourceVO {

    private Long mediaId;

    private String title;

    private String playbackType;

    private String disclaimer;

    private List<PlaySourceItem> sources = new ArrayList<>();

    @Data
    public static class PlaySourceItem {
        private String sourceType;
        private String providerName;
        private String title;
        private String url;
        private String region;
        private String quality;
        private Boolean free;
    }
}
