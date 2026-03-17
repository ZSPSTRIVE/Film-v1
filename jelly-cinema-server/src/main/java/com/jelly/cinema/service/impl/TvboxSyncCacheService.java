package com.jelly.cinema.service.impl;

import com.jelly.cinema.common.config.property.TvboxProxyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;

/**
 * TVBox 同步缓存管理服务
 * 
 * 目的: 
 * 1. 记录最近同步的关键词，避免短时间内重复调用 TVBox
 * 2. 支持异步同步，不阻塞搜索请求
 * 3. 可配置的缓存时间，平衡数据新鲜度与响应速度
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TvboxSyncCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final TvboxMediaIngestService tvboxMediaIngestService;
    private final TvboxProxyProperties tvboxProxyProperties;

    private static final String SYNC_CACHE_PREFIX = "jelly:tvbox:sync-cache:";
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(6);  // 6 小时缓存

    /**
     * 检查是否最近已同步过某个关键词
     * 
     * @param keyword 搜索关键词
     * @return true 表示最近已同步过，无需重复同步
     */
    public boolean isCached(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return false;
        }
        try {
            String cacheKey = buildCacheKey(keyword);
            Boolean exists = stringRedisTemplate.hasKey(cacheKey);
            return exists != null && exists;
        } catch (Exception e) {
            log.warn("TVBox sync cache lookup failed, fallback to uncached mode. keyword={}", keyword, e);
            return false;
        }
    }

    /**
     * 标记某个关键词的同步状态
     * 缓存时间: 6 小时
     * 
     * @param keyword 搜索关键词
     */
    public void markSynced(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        try {
            String cacheKey = buildCacheKey(keyword);
            stringRedisTemplate.opsForValue().set(cacheKey, "1", DEFAULT_CACHE_TTL);
            log.debug("TVBox sync cache marked. keyword={}, ttl={}s", keyword, DEFAULT_CACHE_TTL.toSeconds());
        } catch (Exception e) {
            log.warn("TVBox sync cache mark failed, sync result will not be cached. keyword={}", keyword, e);
        }
    }

    /**
     * 清除某个关键词的缓存（强制重新同步）
     * 
     * @param keyword 搜索关键词
     */
    public void invalidate(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        try {
            String cacheKey = buildCacheKey(keyword);
            stringRedisTemplate.delete(cacheKey);
            log.info("TVBox sync cache invalidated. keyword={}", keyword);
        } catch (Exception e) {
            log.warn("Failed to invalidate TVBox sync cache. keyword={}", keyword, e);
        }
    }

    /**
     * 清除所有 TVBox 同步缓存
     * 用于管理员操作或定时刷新
     */
    public void invalidateAll() {
        try {
            stringRedisTemplate.delete(
                stringRedisTemplate.keys(SYNC_CACHE_PREFIX + "*")
            );
            log.info("All TVBox sync caches invalidated");
        } catch (Exception e) {
            log.warn("Failed to invalidate all TVBox sync caches", e);
        }
    }

    /**
     * 异步同步关键词数据（不等待结果）
     * 
     * 用途: 标准搜索中调用，不阻塞用户请求
     * 调用者会立即返回本地库结果，同步在后台进行
     * 
     * @param keyword 搜索关键词
     * @param limit 获取的最大条目数
     */
    @Async(value = "tvboxAsyncExecutor")
    public void syncAsyncIfNotCached(String keyword, int limit) {
        if (!StringUtils.hasText(keyword) || !tvboxProxyProperties.isEnable()) {
            return;
        }

        // 双重检查：避免并发重复同步
        if (isCached(keyword)) {
            log.debug("TVBox sync skipped (cached). keyword={}", keyword);
            return;
        }

        try {
            TvboxSyncResult syncResult = tvboxMediaIngestService.syncByKeyword(keyword, limit);
            if (syncResult.getSyncedExternalResourceCount() > 0) {
                markSynced(keyword);
                log.info("TVBox async sync completed. keyword={}, externalCount={}, affectedMediaIds={}",
                        keyword, syncResult.getSyncedExternalResourceCount(), syncResult.getAffectedMediaIds().size());
            } else {
                log.debug("TVBox async sync found no new data. keyword={}", keyword);
            }
        } catch (Exception e) {
            log.warn("TVBox async sync failed. keyword={}", keyword, e);
            // 不标记为已缓存，允许下次重试
        }
    }

    /**
     * 同步关键词数据（阻塞模式，用于 AI 搜索）
     * 
     * 用途: AI 搜索中调用，需要确保数据入库后再进行搜索
     * 会检查缓存，已缓存则跳过 HTTP 调用
     * 
     * @param keyword 搜索关键词
     * @param limit 获取的最大条目数
     * @return 本次同步新增/更新的记录数
     */
    public TvboxSyncResult syncIfNotCached(String keyword, int limit) {
        if (!StringUtils.hasText(keyword) || !tvboxProxyProperties.isEnable()) {
            return new TvboxSyncResult();
        }

        if (isCached(keyword)) {
            log.debug("TVBox sync skipped (cached). keyword={}", keyword);
            return new TvboxSyncResult();
        }

        try {
            TvboxSyncResult syncResult = tvboxMediaIngestService.syncByKeyword(keyword, limit);
            if (syncResult.getSyncedExternalResourceCount() > 0) {
                markSynced(keyword);
                log.info("TVBox sync completed. keyword={}, externalCount={}, affectedMediaIds={}",
                        keyword, syncResult.getSyncedExternalResourceCount(), syncResult.getAffectedMediaIds().size());
            }
            return syncResult;
        } catch (Exception e) {
            log.warn("TVBox sync failed. keyword={}", keyword, e);
            return new TvboxSyncResult();
        }
    }

    /**
     * 预热热门关键词缓存
     * 
     * 用途: 应用启动或定时任务中调用
     * 效果: 预先同步常见搜索词，加快用户首页搜索响应时间
     * 
     * @param hotKeywords 热门关键词列表
     */
    public void preWarmHotKeywords(String... hotKeywords) {
        if (hotKeywords == null || hotKeywords.length == 0) {
            return;
        }
        for (String keyword : hotKeywords) {
            asyncWarmKeyword(keyword, 20);
        }
        log.info("TVBox hot keywords pre-warming scheduled. count={}", hotKeywords.length);
    }

    /**
     * 异步预热单个关键词
     */
    @Async(value = "tvboxAsyncExecutor")
    public void asyncWarmKeyword(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        syncAsyncIfNotCached(keyword, limit);
    }

    /**
     * 生成缓存键
     */
    private String buildCacheKey(String keyword) {
        return SYNC_CACHE_PREFIX + keyword.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "_");
    }

    /**
     * 获取缓存统计信息（用于监控）
     * 
     * @return 当前缓存的关键词数量
     */
    public long getCacheSizeInfo() {
        try {
            Integer count = stringRedisTemplate.keys(SYNC_CACHE_PREFIX + "*").size();
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Failed to get TVBox sync cache size", e);
            return -1;
        }
    }
}
