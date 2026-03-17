package com.jelly.cinema.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.common.config.property.TvboxProxyProperties;
import com.jelly.cinema.mapper.MediaExternalResourceMapper;
import com.jelly.cinema.mapper.MediaMapper;
import com.jelly.cinema.model.entity.Media;
import com.jelly.cinema.model.entity.MediaExternalResource;
import com.jelly.cinema.service.ai.rag.RagIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TvboxMediaIngestService {

    private static final Pattern YEAR_PATTERN = Pattern.compile("(19|20)\\d{2}");
    private static final BigDecimal DEFAULT_RATING = BigDecimal.valueOf(6.0d);

    private final TvboxProxyProperties tvboxProxyProperties;
    private final MediaMapper mediaMapper;
    private final MediaExternalResourceMapper mediaExternalResourceMapper;
    private final RagIndexingService ragIndexingService;
    private final RestTemplate restTemplate = new RestTemplate();

    public TvboxSyncResult syncByKeyword(String keyword, int limit) {
        TvboxSyncResult result = new TvboxSyncResult();
        if (!tvboxProxyProperties.isEnable() || !StringUtils.hasText(keyword)) {
            return result;
        }

        int max = Math.max(1, Math.min(Math.max(limit, 1), Math.max(tvboxProxyProperties.getIngestLimit(), 40)));

        try {
            String searchUrl = UriComponentsBuilder
                    .fromHttpUrl(tvboxProxyProperties.getBaseUrl() + "/api/tvbox/search")
                    .queryParam("keyword", keyword)
                    .queryParam("limit", max)
                    .build()
                    .toUriString();

            String response = restTemplate.getForObject(searchUrl, String.class);
            JSONObject searchJson = JSON.parseObject(response);
            JSONArray data = searchJson == null ? null : searchJson.getJSONArray("data");
            if (data == null || data.isEmpty()) {
                return result;
            }

            List<Media> activeMedia = mediaMapper.selectList(new LambdaQueryWrapper<Media>()
                    .eq(Media::getDeleted, 0)
                    .orderByDesc(Media::getUpdateTime, Media::getRating));
            MatchingContext context = MatchingContext.from(activeMedia);

            for (int i = 0; i < data.size() && result.getSyncedExternalResourceCount() < max; i++) {
                JSONObject item = data.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                processItem(item, context, result);
            }

            rebuildAffectedMedia(result);
        } catch (Exception e) {
            log.warn("TVBox keyword ingest failed. keyword={}", keyword, e);
        }

        return result;
    }

    public List<MediaExternalResource> listActiveResourcesForMedia(Long mediaId, int limit) {
        if (mediaId == null) {
            return List.of();
        }
        return mediaExternalResourceMapper.selectList(new LambdaQueryWrapper<MediaExternalResource>()
                        .eq(MediaExternalResource::getDeleted, 0)
                        .eq(MediaExternalResource::getMediaId, mediaId)
                        .in(MediaExternalResource::getSyncStatus, List.of("LINKED", "CREATED"))
                        .orderByDesc(MediaExternalResource::getMatchConfidence, MediaExternalResource::getLastSyncedAt))
                .stream()
                .limit(Math.max(1, limit))
                .toList();
    }

    private void processItem(JSONObject item, MatchingContext context, TvboxSyncResult result) {
        NormalizedResource normalized = normalize(item);
        if (!StringUtils.hasText(normalized.externalItemId()) || !StringUtils.hasText(normalized.rawTitle())) {
            return;
        }

        MediaExternalResource resource = findExistingResource(normalized.providerName(), normalized.externalItemId());
        if (resource == null) {
            resource = new MediaExternalResource();
            resource.setProviderName(normalized.providerName());
            resource.setExternalItemId(normalized.externalItemId());
            resource.setDeleted(0);
        }

        if (normalized.noise()) {
            bindResource(resource, normalized, null, BigDecimal.ZERO, "FILTERED_NOISE");
            upsertExternalResource(resource);
            result.setFilteredNoiseCount(result.getFilteredNoiseCount() + 1);
            result.setSyncedExternalResourceCount(result.getSyncedExternalResourceCount() + 1);
            return;
        }

        MatchDecision decision = matchMedia(normalized, context);
        Media media = decision.media();
        if (media == null) {
            media = createMedia(normalized);
            mediaMapper.insert(media);
            context.add(media);
            result.setCreatedMediaCount(result.getCreatedMediaCount() + 1);
            result.getAffectedMediaIds().add(media.getId());
            decision = new MatchDecision(media, BigDecimal.valueOf(0.7800d), true);
        } else if (mergeMedia(media, normalized)) {
            mediaMapper.updateById(media);
            context.add(media);
            result.setUpdatedMediaCount(result.getUpdatedMediaCount() + 1);
            result.getAffectedMediaIds().add(media.getId());
        }

        bindResource(resource, normalized, media.getId(), decision.confidence(), decision.created() ? "CREATED" : "LINKED");
        upsertExternalResource(resource);
        result.setSyncedExternalResourceCount(result.getSyncedExternalResourceCount() + 1);
        result.getAffectedMediaIds().add(media.getId());
    }

    private void rebuildAffectedMedia(TvboxSyncResult result) {
        if (result.getAffectedMediaIds().isEmpty()) {
            return;
        }
        try {
            ragIndexingService.rebuildMediaIndexes(result.getAffectedMediaIds());
        } catch (Exception e) {
            log.warn("Incremental RAG rebuild failed. mediaIds={}", result.getAffectedMediaIds(), e);
        }
    }

    private MediaExternalResource findExistingResource(String providerName, String externalItemId) {
        return mediaExternalResourceMapper.selectOne(new LambdaQueryWrapper<MediaExternalResource>()
                .eq(MediaExternalResource::getProviderName, providerName)
                .eq(MediaExternalResource::getExternalItemId, externalItemId)
                .last("limit 1"));
    }

    private void upsertExternalResource(MediaExternalResource resource) {
        if (resource.getId() == null) {
            mediaExternalResourceMapper.insert(resource);
        } else {
            mediaExternalResourceMapper.updateById(resource);
        }
    }

    private void bindResource(MediaExternalResource resource,
                              NormalizedResource normalized,
                              Long mediaId,
                              BigDecimal confidence,
                              String syncStatus) {
        resource.setMediaId(mediaId);
        resource.setRawTitle(normalized.rawTitle());
        resource.setCleanTitle(normalized.cleanTitle());
        resource.setReleaseYear(normalized.releaseYear());
        resource.setType(normalized.type());
        resource.setRating(normalized.rating());
        resource.setRegion(normalized.region());
        resource.setDirector(normalized.director());
        resource.setActors(normalized.actors());
        resource.setDescription(normalized.description());
        resource.setCoverUrl(normalized.coverUrl());
        resource.setSourceKey(normalized.sourceKey());
        resource.setRawPayloadJson(normalized.rawPayloadJson());
        resource.setMatchConfidence(confidence);
        resource.setSyncStatus(syncStatus);
        resource.setLastSyncedAt(LocalDateTime.now());
        if (resource.getDeleted() == null) {
            resource.setDeleted(0);
        }
    }

    private NormalizedResource normalize(JSONObject item) {
        String rawTitle = trimToEmpty(item.getString("title"));
        String cleanTitle = ExternalMediaTitleNormalizer.cleanTitle(rawTitle);
        if (!StringUtils.hasText(cleanTitle)) {
            cleanTitle = rawTitle;
        }
        String description = trimToEmpty(item.getString("description"));
        String sourceName = trimToEmpty(item.getString("sourceName"));
        String sourceKey = trimToEmpty(item.getString("sourceKey"));
        String providerName = StringUtils.hasText(sourceName) ? sourceName : defaultText(sourceKey, "TVBox");
        Integer releaseYear = parseYear(item.getString("year"));
        BigDecimal rating = parseRating(item.get("rating"));
        Integer type = inferType(rawTitle, description);
        boolean noise = ExternalMediaTitleNormalizer.isDerivativeNoise(rawTitle);
        return new NormalizedResource(
                trimToEmpty(item.getString("id")),
                providerName,
                sourceKey,
                rawTitle,
                cleanTitle,
                description,
                trimToEmpty(item.getString("coverUrl")),
                trimToEmpty(item.getString("region")),
                trimToEmpty(item.getString("director")),
                trimToEmpty(item.getString("actors")),
                releaseYear,
                rating,
                type,
                noise,
                JSON.toJSONString(item)
        );
    }

    private MatchDecision matchMedia(NormalizedResource normalized, MatchingContext context) {
        List<Media> yearCandidates = context.byCleanTitleAndYear(normalized.cleanTitle(), normalized.releaseYear());
        Media yearMatch = pickBestCandidate(yearCandidates, normalized);
        if (yearMatch != null) {
            return new MatchDecision(yearMatch, BigDecimal.valueOf(0.9800d), false);
        }

        List<Media> titleCandidates = context.byCleanTitle(normalized.cleanTitle());
        Media titleMatch = pickBestCandidate(titleCandidates, normalized);
        if (titleMatch != null) {
            BigDecimal confidence = normalized.rawTitle().equalsIgnoreCase(defaultText(titleMatch.getTitle(), ""))
                    ? BigDecimal.valueOf(0.9400d)
                    : BigDecimal.valueOf(0.8900d);
            return new MatchDecision(titleMatch, confidence, false);
        }

        return new MatchDecision(null, BigDecimal.ZERO, false);
    }

    private Media pickBestCandidate(List<Media> candidates, NormalizedResource normalized) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing((Media media) -> normalized.type() != null && normalized.type().equals(media.getType()))
                        .reversed()
                        .thenComparing(media -> media.getUpdateTime(), Comparator.nullsLast(LocalDateTime::compareTo))
                        .reversed())
                .findFirst()
                .orElse(null);
    }

    private Media createMedia(NormalizedResource normalized) {
        Media media = new Media();
        media.setTitle(normalized.cleanTitle());
        media.setOriginalTitle(buildOriginalTitle(normalized));
        media.setType(normalized.type());
        media.setStatus(2);
        media.setReleaseDate(normalized.releaseYear() == null ? null : LocalDate.of(normalized.releaseYear(), 1, 1));
        media.setDuration(0);
        media.setCoverUrl(normalized.coverUrl());
        media.setBackdropUrl(normalized.coverUrl());
        media.setSummary(normalized.description());
        media.setRating(normalized.rating() == null ? DEFAULT_RATING : normalized.rating());
        media.setDeleted(0);
        return media;
    }

    private boolean mergeMedia(Media media, NormalizedResource normalized) {
        boolean changed = false;
        if ((media.getType() == null || media.getType() == 0) && normalized.type() != null) {
            media.setType(normalized.type());
            changed = true;
        }
        if (media.getReleaseDate() == null && normalized.releaseYear() != null) {
            media.setReleaseDate(LocalDate.of(normalized.releaseYear(), 1, 1));
            changed = true;
        }
        if (!StringUtils.hasText(media.getSummary()) || normalized.description().length() > defaultText(media.getSummary(), "").length()) {
            if (StringUtils.hasText(normalized.description()) && !normalized.description().equals(media.getSummary())) {
                media.setSummary(normalized.description());
                changed = true;
            }
        }
        if (StringUtils.hasText(normalized.coverUrl()) && !normalized.coverUrl().equals(media.getCoverUrl())) {
            media.setCoverUrl(normalized.coverUrl());
            if (!StringUtils.hasText(media.getBackdropUrl())) {
                media.setBackdropUrl(normalized.coverUrl());
            }
            changed = true;
        }
        if (!StringUtils.hasText(media.getOriginalTitle()) && StringUtils.hasText(buildOriginalTitle(normalized))) {
            media.setOriginalTitle(buildOriginalTitle(normalized));
            changed = true;
        }
        if (normalized.rating() != null) {
            BigDecimal current = media.getRating() == null ? BigDecimal.ZERO : media.getRating();
            if (current.compareTo(normalized.rating()) < 0) {
                media.setRating(normalized.rating());
                changed = true;
            }
        }
        return changed;
    }

    private String buildOriginalTitle(NormalizedResource normalized) {
        if (!StringUtils.hasText(normalized.rawTitle())) {
            return normalized.cleanTitle();
        }
        if (normalized.rawTitle().equals(normalized.cleanTitle())) {
            return normalized.rawTitle();
        }
        return normalized.rawTitle();
    }

    private Integer parseYear(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = YEAR_PATTERN.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group());
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal parseRating(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number number) {
                double parsed = Math.max(0.0d, Math.min(10.0d, number.doubleValue()));
                return BigDecimal.valueOf(parsed);
            }
            String text = String.valueOf(value).trim();
            if (!StringUtils.hasText(text)) {
                return null;
            }
            double parsed = Math.max(0.0d, Math.min(10.0d, Double.parseDouble(text)));
            return BigDecimal.valueOf(parsed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer inferType(String title, String description) {
        String normalized = (trimToEmpty(title) + " " + trimToEmpty(description)).toLowerCase(Locale.ROOT);
        if (normalized.contains("动画") || normalized.contains("動漫") || normalized.contains("anime") || normalized.contains("cartoon")) {
            return 3;
        }
        if ((normalized.contains("第") && normalized.contains("集"))
                || normalized.contains("剧")
                || normalized.contains("tv")
                || normalized.contains("series")) {
            return 2;
        }
        return 1;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private record NormalizedResource(String externalItemId,
                                      String providerName,
                                      String sourceKey,
                                      String rawTitle,
                                      String cleanTitle,
                                      String description,
                                      String coverUrl,
                                      String region,
                                      String director,
                                      String actors,
                                      Integer releaseYear,
                                      BigDecimal rating,
                                      Integer type,
                                      boolean noise,
                                      String rawPayloadJson) {
    }

    private record MatchDecision(Media media, BigDecimal confidence, boolean created) {
    }

    private static final class MatchingContext {
        private final List<Media> allMedia;
        private final Map<String, List<Media>> byCleanTitle = new LinkedHashMap<>();
        private final Map<String, List<Media>> byCleanTitleYear = new LinkedHashMap<>();

        private MatchingContext(List<Media> allMedia) {
            this.allMedia = new ArrayList<>(allMedia);
            for (Media media : allMedia) {
                add(media);
            }
        }

        static MatchingContext from(List<Media> allMedia) {
            return new MatchingContext(allMedia == null ? List.of() : allMedia);
        }

        void add(Media media) {
            if (media == null || media.getId() == null) {
                return;
            }
            allMedia.removeIf(existing -> existing.getId().equals(media.getId()));
            allMedia.add(media);

            String primaryKey = titleKey(media.getTitle());
            if (StringUtils.hasText(primaryKey)) {
                byCleanTitle.computeIfAbsent(primaryKey, ignored -> new ArrayList<>());
                merge(primaryKey, media, byCleanTitle);
                if (media.getReleaseDate() != null) {
                    merge(yearKey(primaryKey, media.getReleaseDate().getYear()), media, byCleanTitleYear);
                }
            }

            String originalKey = titleKey(media.getOriginalTitle());
            if (StringUtils.hasText(originalKey) && !originalKey.equals(primaryKey)) {
                merge(originalKey, media, byCleanTitle);
                if (media.getReleaseDate() != null) {
                    merge(yearKey(originalKey, media.getReleaseDate().getYear()), media, byCleanTitleYear);
                }
            }
        }

        List<Media> byCleanTitle(String cleanTitle) {
            return byCleanTitle.getOrDefault(titleKey(cleanTitle), List.of());
        }

        List<Media> byCleanTitleAndYear(String cleanTitle, Integer year) {
            if (year == null) {
                return List.of();
            }
            return byCleanTitleYear.getOrDefault(yearKey(titleKey(cleanTitle), year), List.of());
        }

        private void merge(String key, Media media, Map<String, List<Media>> map) {
            List<Media> items = map.computeIfAbsent(key, ignored -> new ArrayList<>());
            boolean exists = items.stream().anyMatch(existing -> existing.getId().equals(media.getId()));
            if (!exists) {
                items.add(media);
            }
        }

        private String titleKey(String title) {
            return ExternalMediaTitleNormalizer.cleanTitle(title).toLowerCase(Locale.ROOT);
        }

        private String yearKey(String titleKey, Integer year) {
            return titleKey + "#" + year;
        }
    }
}
