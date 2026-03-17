package com.jelly.cinema.common.config.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "tvbox")
public class TvboxProxyProperties {

    private boolean enable = true;

    private String baseUrl = "http://127.0.0.1:3001";

    private int ingestLimit = 20;
}
