package com.jelly.cinema.common.config.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "tmdb")
public class TmdbProperties {
    private String apiKey = "";
    private String baseUrl = "https://api.themoviedb.org/3";
    private String imageBaseUrl = "https://image.tmdb.org/t/p/w500";
    private String originalImageBaseUrl = "https://image.tmdb.org/t/p/original";
}
