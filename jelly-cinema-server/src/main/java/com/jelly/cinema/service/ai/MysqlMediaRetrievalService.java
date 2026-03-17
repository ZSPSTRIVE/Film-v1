package com.jelly.cinema.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.mapper.MediaExternalResourceMapper;
import com.jelly.cinema.mapper.MediaMapper;
import com.jelly.cinema.model.entity.Media;
import com.jelly.cinema.model.entity.MediaExternalResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MysqlMediaRetrievalService implements MediaRetrievalService {

    private final MediaMapper mediaMapper;
    private final MediaExternalResourceMapper mediaExternalResourceMapper;

    @Override
    public List<MediaSearchHit> retrieve(AiSearchPlan plan, int size) {
        AiQueryMode queryMode = resolveQueryMode(plan);
        int candidateLimit = Math.max(size * 4, 24);
        Map<Long, MediaExternalResource> bestExternalMatches = searchExternalMatches(plan, queryMode, candidateLimit);
        Set<Long> extraMediaIds = new LinkedHashSet<>(bestExternalMatches.keySet());

        List<Media> candidates = loadCandidates(plan, queryMode, candidateLimit, extraMediaIds);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, List<MediaExternalResource>> externalByMedia = loadExternalResources(
                candidates.stream().map(Media::getId).collect(Collectors.toSet()));

        return candidates.stream()
                .map(media -> toSearchHit(media, plan, queryMode, bestExternalMatches.get(media.getId()),
                        externalByMedia.getOrDefault(media.getId(), List.of())))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(MediaSearchHit::getRerankScore, Comparator.nullsLast(Double::compareTo)).reversed()
                        .thenComparing(hit -> hit.getMedia().getRating() == null ? BigDecimal.ZERO : hit.getMedia().getRating(), Comparator.reverseOrder())
                        .thenComparing(hit -> hit.getMedia().getReleaseDate(), Comparator.nullsLast(LocalDate::compareTo).reversed()))
                .limit(Math.max(1, size))
                .collect(Collectors.toList());
    }

    private List<Media> loadCandidates(AiSearchPlan plan,
                                       AiQueryMode queryMode,
                                       int candidateLimit,
                                       Set<Long> externalMediaIds) {
        LambdaQueryWrapper<Media> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Media::getDeleted, 0);
        applyHardFilters(wrapper, plan);

        String normalizedQuery = defaultText(plan.getNormalizedQuery(), "").trim();
        if (queryMode == AiQueryMode.TITLE && StringUtils.hasText(normalizedQuery)) {
            wrapper.and(q -> q.like(Media::getTitle, normalizedQuery)
                    .or()
                    .like(Media::getOriginalTitle, normalizedQuery)
                    .or()
                    .like(Media::getSummary, normalizedQuery)
                    .or(!externalMediaIds.isEmpty(), sub -> sub.in(Media::getId, externalMediaIds)));
        } else if (!externalMediaIds.isEmpty()) {
            wrapper.and(q -> q.in(Media::getId, externalMediaIds)
                    .or()
                    .like(StringUtils.hasText(normalizedQuery), Media::getTitle, normalizedQuery)
                    .or()
                    .like(StringUtils.hasText(normalizedQuery), Media::getOriginalTitle, normalizedQuery)
                    .or()
                    .like(StringUtils.hasText(normalizedQuery), Media::getSummary, normalizedQuery));
        }

        if ("releaseDate".equalsIgnoreCase(plan.getSortBy())) {
            wrapper.orderByDesc(Media::getReleaseDate, Media::getRating);
        } else {
            wrapper.orderByDesc(Media::getRating, Media::getReleaseDate);
        }

        wrapper.last("limit " + Math.max(1, candidateLimit));
        return mediaMapper.selectList(wrapper);
    }

    private void applyHardFilters(LambdaQueryWrapper<Media> wrapper, AiSearchPlan plan) {
        if (plan.getType() != null && plan.getType() > 0) {
            wrapper.eq(Media::getType, plan.getType());
        }
        if (plan.getStatus() != null) {
            wrapper.eq(Media::getStatus, plan.getStatus());
        }
        if (plan.getMinRating() != null) {
            wrapper.ge(Media::getRating, BigDecimal.valueOf(plan.getMinRating()));
        }
        if (plan.getYearFrom() != null) {
            wrapper.ge(Media::getReleaseDate, LocalDate.of(plan.getYearFrom(), 1, 1));
        }
        if (plan.getYearTo() != null) {
            wrapper.le(Media::getReleaseDate, LocalDate.of(plan.getYearTo(), 12, 31));
        }
    }

    private Map<Long, MediaExternalResource> searchExternalMatches(AiSearchPlan plan, AiQueryMode queryMode, int limit) {
        String normalizedQuery = defaultText(plan.getNormalizedQuery(), "").trim();
        if (!StringUtils.hasText(normalizedQuery)) {
            return Map.of();
        }

        LambdaQueryWrapper<MediaExternalResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MediaExternalResource::getDeleted, 0)
                .isNotNull(MediaExternalResource::getMediaId)
                .in(MediaExternalResource::getSyncStatus, List.of("LINKED", "CREATED"));
        if (plan.getType() != null && plan.getType() > 0) {
            wrapper.eq(MediaExternalResource::getType, plan.getType());
        }
        if (plan.getMinRating() != null) {
            wrapper.ge(MediaExternalResource::getRating, BigDecimal.valueOf(plan.getMinRating()));
        }
        if (plan.getYearFrom() != null) {
            wrapper.ge(MediaExternalResource::getReleaseYear, plan.getYearFrom());
        }
        if (plan.getYearTo() != null) {
            wrapper.le(MediaExternalResource::getReleaseYear, plan.getYearTo());
        }
        wrapper.and(q -> q.like(MediaExternalResource::getCleanTitle, normalizedQuery)
                .or()
                .like(MediaExternalResource::getRawTitle, normalizedQuery)
                .or()
                .like(MediaExternalResource::getDescription, normalizedQuery)
                .or()
                .like(MediaExternalResource::getDirector, normalizedQuery)
                .or()
                .like(MediaExternalResource::getActors, normalizedQuery)
                .or()
                .like(MediaExternalResource::getRegion, normalizedQuery));
        wrapper.orderByDesc(MediaExternalResource::getMatchConfidence, MediaExternalResource::getLastSyncedAt);
        wrapper.last("limit " + Math.max(1, limit));

        return mediaExternalResourceMapper.selectList(wrapper).stream()
                .filter(resource -> resource.getMediaId() != null)
                .collect(Collectors.toMap(
                        MediaExternalResource::getMediaId,
                        resource -> resource,
                        (left, right) -> externalMatchScore(right, normalizedQuery, queryMode)
                                >= externalMatchScore(left, normalizedQuery, queryMode) ? right : left,
                        LinkedHashMap::new
                ));
    }

    private Map<Long, List<MediaExternalResource>> loadExternalResources(Set<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return Map.of();
        }
        return mediaExternalResourceMapper.selectList(new LambdaQueryWrapper<MediaExternalResource>()
                        .eq(MediaExternalResource::getDeleted, 0)
                        .in(MediaExternalResource::getSyncStatus, List.of("LINKED", "CREATED"))
                        .in(MediaExternalResource::getMediaId, mediaIds)
                        .orderByDesc(MediaExternalResource::getMatchConfidence, MediaExternalResource::getLastSyncedAt))
                .stream()
                .filter(resource -> resource.getMediaId() != null)
                .collect(Collectors.groupingBy(MediaExternalResource::getMediaId, LinkedHashMap::new, Collectors.toList()));
    }

    private MediaSearchHit toSearchHit(Media media,
                                       AiSearchPlan plan,
                                       AiQueryMode queryMode,
                                       MediaExternalResource matchedExternal,
                                       List<MediaExternalResource> supportResources) {
        if (media == null) {
            return null;
        }

        double lexicalScore = switch (queryMode) {
            case TITLE -> scoreTitleLookup(media, matchedExternal, plan.getNormalizedQuery());
            case FILTERED_DISCOVERY -> scoreFilteredDiscovery(media, matchedExternal, supportResources, plan);
            case RECOMMENDATION -> scoreRecommendation(media, matchedExternal, supportResources, plan);
        };

        MediaSearchHit hit = new MediaSearchHit();
        hit.setMedia(media);
        hit.setScore(lexicalScore);
        hit.setLexicalScore(lexicalScore);
        hit.setRerankScore(lexicalScore);
        hit.setHighlight(buildHighlight(media, matchedExternal, supportResources, plan.getNormalizedQuery()));
        hit.setKnowledgeBaseCode(matchedExternal == null ? null : "external_media_kb");
        hit.setSource(matchedExternal == null ? "mysql" : "mysql_external");
        hit.setLexicalSource(hit.getSource());
        return hit;
    }

    private double scoreTitleLookup(Media media, MediaExternalResource external, String normalizedQuery) {
        String query = defaultText(normalizedQuery, "").trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(query)) {
            return 0.18d;
        }
        double score = 0.12d;
        score = Math.max(score, titleScore(query, media.getTitle(), 1.0d, 0.72d));
        score = Math.max(score, titleScore(query, media.getOriginalTitle(), 0.95d, 0.68d));
        score = Math.max(score, textScore(query, media.getSummary(), 0.38d));
        if (external != null) {
            score = Math.max(score, titleScore(query, external.getCleanTitle(), 0.92d, 0.70d));
            score = Math.max(score, titleScore(query, external.getRawTitle(), 0.88d, 0.66d));
            score = Math.max(score, textScore(query, external.getDescription(), 0.42d));
        }
        return score;
    }

    private double scoreFilteredDiscovery(Media media,
                                          MediaExternalResource external,
                                          List<MediaExternalResource> supportResources,
                                          AiSearchPlan plan) {
        double score = 0.16d;
        score += qualityBonus(media, external) * 0.35d;
        score += freshnessBonus(media, external, plan) * 0.28d;
        score += softTermBonus(plan, media, mergeResources(external, supportResources)) * 0.32d;
        if (external != null && external.getMatchConfidence() != null) {
            score += external.getMatchConfidence().doubleValue() * 0.12d;
        }
        return score;
    }

    private double scoreRecommendation(Media media,
                                       MediaExternalResource external,
                                       List<MediaExternalResource> supportResources,
                                       AiSearchPlan plan) {
        double score = 0.20d;
        score += qualityBonus(media, external) * 0.40d;
        score += freshnessBonus(media, external, plan) * 0.18d;
        score += softTermBonus(plan, media, mergeResources(external, supportResources)) * 0.18d;
        if (external != null && external.getMatchConfidence() != null) {
            score += external.getMatchConfidence().doubleValue() * 0.08d;
        }
        return score;
    }

    private Collection<MediaExternalResource> mergeResources(MediaExternalResource external,
                                                             List<MediaExternalResource> supportResources) {
        List<MediaExternalResource> merged = new ArrayList<>();
        if (external != null) {
            merged.add(external);
        }
        if (supportResources != null) {
            for (MediaExternalResource support : supportResources) {
                if (support != null && merged.stream().noneMatch(item -> item.getId() != null && item.getId().equals(support.getId()))) {
                    merged.add(support);
                }
            }
        }
        return merged;
    }

    private double softTermBonus(AiSearchPlan plan, Media media, Collection<MediaExternalResource> supportResources) {
        String supportText = buildSupportText(media, supportResources);
        List<String> terms = AiSearchQueryHelper.extractSoftTerms(plan.getSemanticQuery(), plan.getNormalizedQuery());
        if (terms.isEmpty()) {
            return 0.0d;
        }
        double score = 0.0d;
        for (String term : terms) {
            if (supportText.contains(term.toLowerCase(Locale.ROOT))) {
                score += 0.12d;
            }
        }
        return Math.min(score, 0.48d);
    }

    private double qualityBonus(Media media, MediaExternalResource external) {
        BigDecimal rating = media.getRating();
        if ((rating == null || rating.signum() == 0) && external != null) {
            rating = external.getRating();
        }
        double numeric = rating == null ? 0.0d : rating.doubleValue();
        return Math.max(0.0d, Math.min(1.0d, numeric / 10.0d));
    }

    private double freshnessBonus(Media media, MediaExternalResource external, AiSearchPlan plan) {
        int referenceYear = Year.now().getValue();
        Integer releaseYear = media.getReleaseDate() == null ? null : media.getReleaseDate().getYear();
        if (releaseYear == null && external != null) {
            releaseYear = external.getReleaseYear();
        }
        if (releaseYear == null) {
            return 0.0d;
        }
        if (plan.getYearFrom() != null && releaseYear < plan.getYearFrom()) {
            return 0.0d;
        }
        int distance = Math.max(0, referenceYear - releaseYear);
        return Math.max(0.0d, 1.0d - (distance / 8.0d));
    }

    private String buildSupportText(Media media, Collection<MediaExternalResource> supportResources) {
        StringBuilder builder = new StringBuilder();
        appendText(builder, media.getTitle());
        appendText(builder, media.getOriginalTitle());
        appendText(builder, media.getSummary());
        for (MediaExternalResource resource : supportResources) {
            appendText(builder, resource.getCleanTitle());
            appendText(builder, resource.getRawTitle());
            appendText(builder, resource.getDescription());
            appendText(builder, resource.getDirector());
            appendText(builder, resource.getActors());
            appendText(builder, resource.getRegion());
            appendText(builder, resource.getProviderName());
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private String buildHighlight(Media media,
                                  MediaExternalResource matchedExternal,
                                  List<MediaExternalResource> supportResources,
                                  String normalizedQuery) {
        String query = defaultText(normalizedQuery, "").trim();
        if (matchedExternal != null) {
            String externalSnippet = snippet(matchedExternal.getDescription(), query);
            if (StringUtils.hasText(externalSnippet)) {
                return externalSnippet;
            }
            if (StringUtils.hasText(matchedExternal.getRawTitle())) {
                return matchedExternal.getRawTitle();
            }
        }
        String summarySnippet = snippet(media.getSummary(), query);
        if (StringUtils.hasText(summarySnippet)) {
            return summarySnippet;
        }
        if (supportResources != null) {
            for (MediaExternalResource support : supportResources) {
                String supportSnippet = snippet(support.getDescription(), query);
                if (StringUtils.hasText(supportSnippet)) {
                    return supportSnippet;
                }
            }
        }
        return media.getTitle();
    }

    private String snippet(String text, String query) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (!StringUtils.hasText(query)) {
            return text.substring(0, Math.min(text.length(), 96));
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        int index = lowerText.indexOf(lowerQuery);
        if (index < 0) {
            return text.substring(0, Math.min(text.length(), 96));
        }
        int start = Math.max(0, index - 18);
        int end = Math.min(text.length(), index + query.length() + 60);
        return text.substring(start, end);
    }

    private double titleScore(String query, String text, double exactScore, double containsScore) {
        if (!StringUtils.hasText(text)) {
            return 0.0d;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals(query)) {
            return exactScore;
        }
        if (normalized.contains(query)) {
            return containsScore;
        }
        return 0.0d;
    }

    private double textScore(String query, String text, double containsScore) {
        if (!StringUtils.hasText(text)) {
            return 0.0d;
        }
        return text.toLowerCase(Locale.ROOT).contains(query) ? containsScore : 0.0d;
    }

    private double externalMatchScore(MediaExternalResource resource, String query, AiQueryMode queryMode) {
        double score = 0.0d;
        score = Math.max(score, titleScore(query.toLowerCase(Locale.ROOT), resource.getCleanTitle(), 1.0d, 0.76d));
        score = Math.max(score, titleScore(query.toLowerCase(Locale.ROOT), resource.getRawTitle(), 0.96d, 0.72d));
        score = Math.max(score, textScore(query.toLowerCase(Locale.ROOT), resource.getDescription(), 0.42d));
        score = Math.max(score, textScore(query.toLowerCase(Locale.ROOT), resource.getActors(), 0.24d));
        score = Math.max(score, textScore(query.toLowerCase(Locale.ROOT), resource.getDirector(), 0.24d));
        if (queryMode != AiQueryMode.TITLE && resource.getMatchConfidence() != null) {
            score += resource.getMatchConfidence().doubleValue() * 0.12d;
        }
        return score;
    }

    private void appendText(StringBuilder builder, String text) {
        if (StringUtils.hasText(text)) {
            builder.append(text).append(' ');
        }
    }

    private AiQueryMode resolveQueryMode(AiSearchPlan plan) {
        if (plan != null && StringUtils.hasText(plan.getQueryMode())) {
            try {
                return AiQueryMode.valueOf(plan.getQueryMode());
            } catch (IllegalArgumentException ignored) {
                // Fallback to heuristic classification below.
            }
        }
        return AiSearchQueryHelper.classify(
                plan == null ? null : plan.getSemanticQuery(),
                plan == null ? null : plan.getNormalizedQuery()
        );
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
