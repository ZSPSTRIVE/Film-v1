package com.jelly.cinema.service.ai;

import com.jelly.cinema.common.config.property.AiProperties;
import com.jelly.cinema.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAccessGuardService {

    private static final DateTimeFormatter WINDOW_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final StringRedisTemplate stringRedisTemplate;
    private final AiProperties aiProperties;

    public void assertAllowed(String sceneCode) {
        if (!aiProperties.getRateLimit().isEnable()) {
            return;
        }

        String clientId = resolveClientId();
        String timeWindow = LocalDateTime.now().format(WINDOW_FORMATTER);
        String key = "jelly:ai:rate:" + sceneCode + ":" + clientId + ":" + timeWindow;

        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, Duration.ofMinutes(1));
            }
            if (count != null && count > aiProperties.getRateLimit().getPerMinute()) {
                throw new BusinessException(429, "AI 请求过于频繁，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to enforce AI rate limit, sceneCode={}", sceneCode, e);
        }
    }

    private String resolveClientId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "anonymous";
        }

        HttpServletRequest request = attributes.getRequest();
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization)) {
            return "token_" + Integer.toHexString(authorization.hashCode());
        }

        return Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .map(it -> it.split(",")[0].trim())
                .filter(StringUtils::hasText)
                .orElseGet(request::getRemoteAddr);
    }
}
