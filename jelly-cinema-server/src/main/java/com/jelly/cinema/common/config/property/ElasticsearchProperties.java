package com.jelly.cinema.common.config.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "search.elasticsearch")
public class ElasticsearchProperties {

    private boolean enable;

    private String baseUrl = "http://127.0.0.1:9200";

    private String mediaIndex = "jelly_media";

    private String username;

    private String password;
}
