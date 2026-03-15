package com.jelly.cinema.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jelly.cinema.common.config.property.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    public String buildKey(String namespace, String rawKey) {
        String digest = DigestUtils.md5DigestAsHex(rawKey.getBytes(StandardCharsets.UTF_8));
        return "jelly:ai:" + namespace + ":" + digest;
    }

    public <T> T get(String key, Class<T> type) {
        if (!aiProperties.getCache().isEnable()) {
            return null;
        }
        try {
            String payload = stringRedisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(payload)) {
                return null;
            }
            return objectMapper.readValue(payload, type);
        } catch (Exception e) {
            log.warn("Failed to read AI cache, key={}", key, e);
            return null;
        }
    }

    public void put(String key, Object value, Duration ttl) {
        if (!aiProperties.getCache().isEnable() || value == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize AI cache, key={}", key, e);
        } catch (Exception e) {
            log.warn("Failed to write AI cache, key={}", key, e);
        }
    }
}
