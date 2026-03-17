package com.jelly.cinema.service.impl;

import com.jelly.cinema.common.config.property.TvboxProxyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TvboxSyncCacheServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private TvboxMediaIngestService tvboxMediaIngestService;

    @Mock
    private TvboxProxyProperties tvboxProxyProperties;

    private TvboxSyncCacheService service;

    @BeforeEach
    void setUp() {
        service = new TvboxSyncCacheService(stringRedisTemplate, tvboxMediaIngestService, tvboxProxyProperties);
    }

    @Test
    void shouldFallbackToUncachedModeWhenRedisLookupFails() {
        when(stringRedisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(service.isCached("钢铁侠")).isFalse();
    }

    @Test
    void shouldSkipSyncWhenKeywordAlreadyCached() {
        when(tvboxProxyProperties.isEnable()).thenReturn(true);
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(true);

        TvboxSyncResult result = service.syncIfNotCached("钢铁侠", 24);

        assertThat(result.getSyncedExternalResourceCount()).isZero();
        verifyNoInteractions(tvboxMediaIngestService);
    }

    @Test
    void shouldMarkCacheAfterSuccessfulSync() {
        when(tvboxProxyProperties.isEnable()).thenReturn(true);
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        TvboxSyncResult syncResult = new TvboxSyncResult();
        syncResult.setSyncedExternalResourceCount(2);
        syncResult.getAffectedMediaIds().add(1L);
        when(tvboxMediaIngestService.syncByKeyword("钢铁侠", 24)).thenReturn(syncResult);

        TvboxSyncResult result = service.syncIfNotCached("钢铁侠", 24);

        assertThat(result).isSameAs(syncResult);
        verify(valueOperations).set(anyString(), eq("1"), any(Duration.class));
    }

    @Test
    void shouldNotMarkCacheWhenSyncReturnsNoData() {
        when(tvboxProxyProperties.isEnable()).thenReturn(true);
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);

        TvboxSyncResult syncResult = new TvboxSyncResult();
        when(tvboxMediaIngestService.syncByKeyword("冷门词", 24)).thenReturn(syncResult);

        TvboxSyncResult result = service.syncIfNotCached("冷门词", 24);

        assertThat(result.getSyncedExternalResourceCount()).isZero();
        verify(stringRedisTemplate, never()).opsForValue();
    }
}
