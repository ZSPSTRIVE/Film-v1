package com.jelly.cinema.common.config.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cos.client")
public class CosProperties {
    private String host;
    private String secretId;
    private String secretKey;
    private String region;
    private String bucket;
    private String appId;
}
