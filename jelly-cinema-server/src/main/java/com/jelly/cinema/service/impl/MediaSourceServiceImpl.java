package com.jelly.cinema.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.common.config.property.TmdbProperties;
import com.jelly.cinema.common.config.property.TvboxProxyProperties;
import com.jelly.cinema.common.exception.BusinessException;
import com.jelly.cinema.mapper.MediaMapper;
import com.jelly.cinema.mapper.MediaPlaySourceMapper;
import com.jelly.cinema.model.entity.Media;
import com.jelly.cinema.model.entity.MediaExternalResource;
import com.jelly.cinema.model.entity.MediaPlaySource;
import com.jelly.cinema.model.vo.MediaPlaySourceVO;
import com.jelly.cinema.service.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaSourceServiceImpl implements MediaSourceService {

    private static final String TVBOX_SOURCE_TYPE = "tvbox_proxy";
    private static final int TVBOX_REFRESH_HOURS = 6;

    private final MediaMapper mediaMapper;
    private final MediaPlaySourceMapper mediaPlaySourceMapper;
    private final TmdbProperties tmdbProperties;
    private final TvboxProxyProperties tvboxProxyProperties;
    private final TvboxMediaIngestService tvboxMediaIngestService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public MediaPlaySourceVO getPlaySources(Long mediaId) {
        Media media = requireMedia(mediaId);

        MediaPlaySourceVO vo = baseResponse(media);
        ensureOfficialSources(media, vo);
        refreshTvboxSourcesIfNeeded(media);

        List<MediaPlaySource> persisted = loadPersistedSources(mediaId);
        if (!persisted.isEmpty()) {
            vo.setSources(persisted.stream().map(this::toVo).toList());
        }
        return vo;
    }

    @Override
    public int syncTvboxSources(Long mediaId) {
        return syncTvboxSourcesInternal(requireMedia(mediaId), true);
    }

    private Media requireMedia(Long mediaId) {
        Media media = mediaMapper.selectById(mediaId);
        if (media == null || media.getDeleted() == 1) {
            throw new BusinessException(404, "Media not found");
        }
        return media;
    }

    private MediaPlaySourceVO baseResponse(Media media) {
        MediaPlaySourceVO vo = new MediaPlaySourceVO();
        vo.setMediaId(media.getId());
        vo.setTitle(media.getTitle());
        vo.setPlaybackType("official_only");
        vo.setDisclaimer("仅聚合官方或平台入口，不提供未授权盗播资源。");
        return vo;
    }

    private void ensureOfficialSources(Media media, MediaPlaySourceVO vo) {
        List<MediaPlaySource> officialPersisted = mediaPlaySourceMapper.selectList(new LambdaQueryWrapper<MediaPlaySource>()
                .eq(MediaPlaySource::getMediaId, media.getId())
                .eq(MediaPlaySource::getDeleted, 0)
                .ne(MediaPlaySource::getSourceType, TVBOX_SOURCE_TYPE)
                .orderByAsc(MediaPlaySource::getSortOrder, MediaPlaySource::getId));
        if (!officialPersisted.isEmpty() || !StringUtils.hasText(tmdbProperties.getApiKey())) {
            return;
        }

        try {
            Long tmdbId = findTmdbId(media);
            if (tmdbId == null) {
                return;
            }
            appendOfficialTrailers(vo, media, tmdbId);
            appendWatchProviders(vo, media, tmdbId);
            persistSources(media.getId(), vo.getSources());
        } catch (Exception e) {
            log.warn("Failed to load official play sources. mediaId={}", media.getId(), e);
        }
    }

    private void refreshTvboxSourcesIfNeeded(Media media) {
        if (!tvboxProxyProperties.isEnable() || !needsTvboxRefresh(media.getId())) {
            return;
        }
        syncTvboxSourcesInternal(media, false);
    }

    private boolean needsTvboxRefresh(Long mediaId) {
        List<MediaPlaySource> sources = mediaPlaySourceMapper.selectList(new LambdaQueryWrapper<MediaPlaySource>()
                .eq(MediaPlaySource::getMediaId, mediaId)
                .eq(MediaPlaySource::getDeleted, 0)
                .eq(MediaPlaySource::getSourceType, TVBOX_SOURCE_TYPE)
                .orderByDesc(MediaPlaySource::getUpdateTime, MediaPlaySource::getCreateTime));
        if (sources.isEmpty()) {
            return true;
        }
        LocalDateTime latest = sources.stream()
                .map(source -> source.getUpdateTime() != null ? source.getUpdateTime() : source.getCreateTime())
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return latest == null || latest.isBefore(LocalDateTime.now().minusHours(TVBOX_REFRESH_HOURS));
    }

    private List<MediaPlaySource> loadPersistedSources(Long mediaId) {
        return mediaPlaySourceMapper.selectList(new LambdaQueryWrapper<MediaPlaySource>()
                .eq(MediaPlaySource::getMediaId, mediaId)
                .eq(MediaPlaySource::getDeleted, 0)
                .orderByAsc(MediaPlaySource::getSortOrder, MediaPlaySource::getId));
    }

    private int syncTvboxSourcesInternal(Media media, boolean forceRefresh) {
        if (!tvboxProxyProperties.isEnable()) {
            return 0;
        }

        String keyword = StringUtils.hasText(media.getTitle()) ? media.getTitle() : media.getOriginalTitle();
        if (!StringUtils.hasText(keyword)) {
            return 0;
        }

        TvboxSyncResult syncResult = tvboxMediaIngestService.syncByKeyword(keyword, Math.max(tvboxProxyProperties.getIngestLimit(), 12));
        List<MediaExternalResource> resources = tvboxMediaIngestService.listActiveResourcesForMedia(
                media.getId(), Math.max(1, tvboxProxyProperties.getIngestLimit()));
        if (resources.isEmpty() && StringUtils.hasText(media.getOriginalTitle()) && !media.getOriginalTitle().equals(media.getTitle())) {
            tvboxMediaIngestService.syncByKeyword(media.getOriginalTitle(), Math.max(tvboxProxyProperties.getIngestLimit(), 12));
            resources = tvboxMediaIngestService.listActiveResourcesForMedia(
                    media.getId(), Math.max(1, tvboxProxyProperties.getIngestLimit()));
        }
        if (resources.isEmpty()) {
            return forceRefresh ? 0 : syncResult.getSyncedExternalResourceCount();
        }

        mediaPlaySourceMapper.delete(new LambdaQueryWrapper<MediaPlaySource>()
                .eq(MediaPlaySource::getMediaId, media.getId())
                .eq(MediaPlaySource::getSourceType, TVBOX_SOURCE_TYPE));

        int inserted = 0;
        int sortOrder = 1;
        int max = Math.max(1, tvboxProxyProperties.getIngestLimit());
        Set<String> insertedUrls = new HashSet<>();

        for (MediaExternalResource resource : resources) {
            if (inserted >= max) {
                break;
            }
            String playApiUrl = tvboxProxyProperties.getBaseUrl() + "/api/tvbox/play/"
                    + UriUtils.encodePathSegment(resource.getExternalItemId(), StandardCharsets.UTF_8);
            try {
                String playResp = restTemplate.getForObject(playApiUrl, String.class);
                JSONObject playJson = JSON.parseObject(playResp);
                JSONObject playData = playJson == null ? null : playJson.getJSONObject("data");
                if (playData == null) {
                    continue;
                }

                String bestUrl = playData.getString("playUrl");
                if (StringUtils.hasText(bestUrl) && inserted < max) {
                    MediaPlaySource source = buildTvboxSource(media.getId(), resource,
                            defaultText(resource.getRawTitle(), media.getTitle()), bestUrl, sortOrder++);
                    if (insertedUrls.add(normalizeUrlForDedup(bestUrl)) && insertTvboxSourceSafely(source)) {
                        inserted++;
                    }
                }

                JSONArray episodes = playData.getJSONArray("episodes");
                if (episodes == null) {
                    continue;
                }
                for (int i = 0; i < episodes.size() && inserted < max; i++) {
                    JSONObject episode = episodes.getJSONObject(i);
                    String url = episode == null ? null : episode.getString("url");
                    if (!StringUtils.hasText(url)) {
                        continue;
                    }
                    String dedupUrl = normalizeUrlForDedup(url);
                    if (!insertedUrls.add(dedupUrl)) {
                        continue;
                    }
                    String title = episode.getString("name");
                    MediaPlaySource source = buildTvboxSource(media.getId(), resource,
                            defaultText(title, defaultText(resource.getRawTitle(), media.getTitle())), url, sortOrder++);
                    if (insertTvboxSourceSafely(source)) {
                        inserted++;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load TVBox play info. mediaId={}, externalItemId={}", media.getId(), resource.getExternalItemId(), e);
            }
        }
        return inserted;
    }

    private MediaPlaySource buildTvboxSource(Long mediaId,
                                             MediaExternalResource resource,
                                             String title,
                                             String url,
                                             int sortOrder) {
        MediaPlaySource source = new MediaPlaySource();
        source.setMediaId(mediaId);
        source.setSourceType(TVBOX_SOURCE_TYPE);
        source.setProviderName(defaultText(resource.getProviderName(), "TVBox Proxy"));
        source.setTitle(title);
        source.setUrl(url);
        source.setRegion(defaultText(resource.getRegion(), "GLOBAL"));
        source.setQuality(url.contains("m3u8") ? "M3U8" : "STD");
        source.setIsFree(1);
        source.setSortOrder(sortOrder);
        source.setDeleted(0);
        return source;
    }

    private boolean insertTvboxSourceSafely(MediaPlaySource source) {
        try {
            Long existing = mediaPlaySourceMapper.selectCount(new LambdaQueryWrapper<MediaPlaySource>()
                    .eq(MediaPlaySource::getMediaId, source.getMediaId())
                    .eq(MediaPlaySource::getSourceType, source.getSourceType())
                    .eq(MediaPlaySource::getProviderName, source.getProviderName())
                    .eq(MediaPlaySource::getUrl, source.getUrl())
                    .eq(MediaPlaySource::getDeleted, 0));
            if (existing != null && existing > 0) {
                return false;
            }

            mediaPlaySourceMapper.insert(source);
            return true;
        } catch (RuntimeException e) {
            if (isDuplicateKeyException(e)) {
                return false;
            }
            throw e;
        }
    }

    private boolean isDuplicateKeyException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("Duplicate entry") || message.contains("uk_media_play_source_unique"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String normalizeUrlForDedup(String url) {
        return url == null ? "" : url.trim();
    }

    private MediaPlaySourceVO.PlaySourceItem toVo(MediaPlaySource entity) {
        MediaPlaySourceVO.PlaySourceItem item = new MediaPlaySourceVO.PlaySourceItem();
        item.setSourceType(entity.getSourceType());
        item.setProviderName(entity.getProviderName());
        item.setTitle(entity.getTitle());
        item.setUrl(entity.getUrl());
        item.setRegion(entity.getRegion());
        item.setQuality(entity.getQuality());
        item.setFree(entity.getIsFree() != null && entity.getIsFree() == 1);
        return item;
    }

    private void persistSources(Long mediaId, List<MediaPlaySourceVO.PlaySourceItem> sources) {
        if (sources == null || sources.isEmpty()) {
            return;
        }

        List<MediaPlaySource> entities = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            MediaPlaySourceVO.PlaySourceItem source = sources.get(i);
            if (!StringUtils.hasText(source.getUrl())) {
                continue;
            }

            MediaPlaySource entity = new MediaPlaySource();
            entity.setMediaId(mediaId);
            entity.setSourceType(source.getSourceType());
            entity.setProviderName(StringUtils.hasText(source.getProviderName()) ? source.getProviderName() : "Official");
            entity.setTitle(StringUtils.hasText(source.getTitle()) ? source.getTitle() : entity.getProviderName());
            entity.setUrl(source.getUrl());
            entity.setRegion(source.getRegion());
            entity.setQuality(source.getQuality());
            entity.setIsFree(Boolean.TRUE.equals(source.getFree()) ? 1 : 0);
            entity.setSortOrder(i + 1);
            entity.setDeleted(0);
            entities.add(entity);
        }

        if (entities.isEmpty()) {
            return;
        }

        for (MediaPlaySource entity : entities) {
            Long existing = mediaPlaySourceMapper.selectCount(new LambdaQueryWrapper<MediaPlaySource>()
                    .eq(MediaPlaySource::getMediaId, entity.getMediaId())
                    .eq(MediaPlaySource::getSourceType, entity.getSourceType())
                    .eq(MediaPlaySource::getProviderName, entity.getProviderName())
                    .eq(MediaPlaySource::getUrl, entity.getUrl())
                    .eq(MediaPlaySource::getDeleted, 0));
            if (existing == null || existing == 0L) {
                mediaPlaySourceMapper.insert(entity);
            }
        }
    }

    private Long findTmdbId(Media media) {
        String endpoint = media.getType() != null && media.getType() == 2 ? "/search/tv" : "/search/movie";
        String query = StringUtils.hasText(media.getOriginalTitle()) ? media.getOriginalTitle() : media.getTitle();
        String encoded = UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8);
        String url = tmdbProperties.getBaseUrl() + endpoint + "?api_key=" + tmdbProperties.getApiKey()
                + "&language=zh-CN&page=1&query=" + encoded;

        String response = restTemplate.getForObject(url, String.class);
        JSONObject json = JSON.parseObject(response);
        JSONArray results = json.getJSONArray("results");
        if (results == null || results.isEmpty()) {
            return null;
        }
        JSONObject first = results.getJSONObject(0);
        return first.getLong("id");
    }

    private void appendOfficialTrailers(MediaPlaySourceVO vo, Media media, Long tmdbId) {
        String endpoint = media.getType() != null && media.getType() == 2 ? "/tv/" : "/movie/";
        String url = tmdbProperties.getBaseUrl() + endpoint + tmdbId + "/videos?api_key=" + tmdbProperties.getApiKey() + "&language=zh-CN";

        String response = restTemplate.getForObject(url, String.class);
        JSONObject json = JSON.parseObject(response);
        JSONArray results = json.getJSONArray("results");
        if (results == null) {
            return;
        }

        for (int i = 0; i < results.size(); i++) {
            JSONObject item = results.getJSONObject(i);
            String site = item.getString("site");
            String key = item.getString("key");
            if (!"YouTube".equalsIgnoreCase(site) || !StringUtils.hasText(key)) {
                continue;
            }

            MediaPlaySourceVO.PlaySourceItem source = new MediaPlaySourceVO.PlaySourceItem();
            source.setSourceType("trailer");
            source.setProviderName("YouTube");
            source.setTitle(item.getString("name"));
            source.setUrl("https://www.youtube.com/watch?v=" + key);
            source.setRegion("GLOBAL");
            source.setQuality(item.getString("type"));
            source.setFree(Boolean.TRUE);
            vo.getSources().add(source);
        }
    }

    private void appendWatchProviders(MediaPlaySourceVO vo, Media media, Long tmdbId) {
        String endpoint = media.getType() != null && media.getType() == 2 ? "/tv/" : "/movie/";
        String url = tmdbProperties.getBaseUrl() + endpoint + tmdbId + "/watch/providers?api_key=" + tmdbProperties.getApiKey();

        String response = restTemplate.getForObject(url, String.class);
        JSONObject json = JSON.parseObject(response);
        JSONObject results = json.getJSONObject("results");
        if (results == null || results.isEmpty()) {
            return;
        }

        Map<String, String> regionPriority = new LinkedHashMap<>();
        regionPriority.put("CN", "中国");
        regionPriority.put("US", "美国");

        for (Map.Entry<String, String> entry : regionPriority.entrySet()) {
            JSONObject regionNode = results.getJSONObject(entry.getKey());
            if (regionNode == null) {
                continue;
            }

            String providerLink = regionNode.getString("link");
            appendProviderItems(vo, regionNode.getJSONArray("flatrate"), entry.getValue(), providerLink, true);
            appendProviderItems(vo, regionNode.getJSONArray("rent"), entry.getValue(), providerLink, false);
            appendProviderItems(vo, regionNode.getJSONArray("buy"), entry.getValue(), providerLink, false);
        }
    }

    private void appendProviderItems(MediaPlaySourceVO vo, JSONArray items, String region, String link, boolean maybeFree) {
        if (items == null) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            MediaPlaySourceVO.PlaySourceItem source = new MediaPlaySourceVO.PlaySourceItem();
            source.setSourceType("watch_provider");
            source.setProviderName(item.getString("provider_name"));
            source.setTitle(item.getString("provider_name") + " 官方入口");
            source.setUrl(link);
            source.setRegion(region);
            source.setQuality("HD");
            source.setFree(maybeFree);
            vo.getSources().add(source);
        }
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
