package com.jelly.cinema.common.config.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private boolean enable;
    private String key;
    private List<Provider> providers;
    private Failover failover;
    private Cache cache = new Cache();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Provider {
        private String name;
        private int priority;
        private boolean enabled;
        private String apiKey;
        private String baseUrl;
        private String model;
        private double temperature;
        private int maxTokens;
        private int timeout;
    }

    @Data
    public static class Failover {
        private int failureThreshold;
        private int timeout;
        private int healthCheckInterval;
        private int retryCount;
        private int retryInterval;
    }

    @Data
    public static class Cache {
        private boolean enable = true;
        private long defaultTtlSeconds = 1800;
        private long summaryTtlSeconds = 21600;
        private long searchTtlSeconds = 900;
    }

    @Data
    public static class RateLimit {
        private boolean enable = true;
        private int perMinute = 30;
    }
}
