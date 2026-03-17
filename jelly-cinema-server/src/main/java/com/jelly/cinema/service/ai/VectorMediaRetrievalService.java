package com.jelly.cinema.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.common.config.property.RagProperties;
import com.jelly.cinema.mapper.MediaMapper;
import com.jelly.cinema.model.entity.Media;
import com.jelly.cinema.service.ai.rag.RagEmbeddingService;
import com.jelly.cinema.service.ai.rag.RagKnowledgeBaseDefinition;
import com.jelly.cinema.service.ai.rag.RagMilvusService;
import com.jelly.cinema.service.ai.rag.RagVectorHit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorMediaRetrievalService implements MediaRetrievalService {

    private final RagProperties ragProperties;
    private final RagEmbeddingService ragEmbeddingService;
    private final RagMilvusService ragMilvusService;
    private final MediaMapper mediaMapper;

    @Override
    public List<MediaSearchHit> retrieve(AiSearchPlan plan, int size) {
        if (!ragProperties.isEnable() || !ragProperties.getMilvus().isEnable()) {
            return List.of();
        }

        String semanticQuery = StringUtils.hasText(plan.getSemanticQuery()) ? plan.getSemanticQuery() : plan.getNormalizedQuery();
        if (!StringUtils.hasText(semanticQuery)) {
            return List.of();
        }

        try {
            List<Float> vector = ragEmbeddingService.embed(semanticQuery);
            int topK = Math.max(size * 2, ragProperties.getRetrieval().getSearchTopK());

            List<RagVectorHit> vectorHits = new ArrayList<>();
            vectorHits.addAll(ragMilvusService.search(
                    ragProperties.getCollections().getMediaProfile(),
                    RagKnowledgeBaseDefinition.MEDIA_PROFILE.getCode(),
                    vector,
                    null,
                    topK
            ));
            vectorHits.addAll(ragMilvusService.search(
                    ragProperties.getCollections().getCommentQa(),
                    RagKnowledgeBaseDefinition.COMMENT_QA.getCode(),
                    vector,
                    null,
                    topK
            ));
            vectorHits.addAll(ragMilvusService.search(
                    ragProperties.getCollections().getExternalMedia(),
                    RagKnowledgeBaseDefinition.EXTERNAL_MEDIA.getCode(),
                    vector,
                    null,
                    topK
            ));

            if (vectorHits.isEmpty()) {
                return List.of();
            }

            Map<Long, RagVectorHit> bestHitByMedia = new LinkedHashMap<>();
            for (RagVectorHit hit : vectorHits) {
                if (hit.getBizId() == null) {
                    continue;
                }
                bestHitByMedia.merge(hit.getBizId(), hit, (left, right) ->
                        score(right) > score(left) ? right : left);
            }

            if (bestHitByMedia.isEmpty()) {
                return List.of();
            }

            Map<Long, Media> mediaMap = mediaMapper.selectList(new LambdaQueryWrapper<Media>()
                            .in(Media::getId, bestHitByMedia.keySet())
                            .eq(Media::getDeleted, 0))
                    .stream()
                    .filter(media -> matchesFilters(media, plan))
                    .collect(java.util.stream.Collectors.toMap(Media::getId, media -> media));

            return bestHitByMedia.entrySet().stream()
                    .map(entry -> toSearchHit(entry.getValue(), mediaMap.get(entry.getKey())))
                    .filter(hit -> hit.getMedia() != null)
                    .sorted(Comparator.comparing(MediaSearchHit::getScore, Comparator.nullsLast(Double::compareTo)).reversed())
                    .limit(size)
                    .toList();
        } catch (Exception e) {
            log.warn("Vector retrieval failed, ignore this branch. plan={}", plan, e);
            return List.of();
        }
    }

    private MediaSearchHit toSearchHit(RagVectorHit vectorHit, Media media) {
        if (media == null) {
            return new MediaSearchHit();
        }
        MediaSearchHit hit = new MediaSearchHit();
        hit.setMedia(media);
        hit.setScore(score(vectorHit));
        hit.setVectorScore(score(vectorHit));
        hit.setHighlight(vectorHit.getChunkText());
        hit.setSource("milvus_vector");
        hit.setKnowledgeBaseCode(vectorHit.getKnowledgeBaseCode());
        return hit;
    }

    private boolean matchesFilters(Media media, AiSearchPlan plan) {
        if (plan.getType() != null && plan.getType() > 0 && !plan.getType().equals(media.getType())) {
            return false;
        }
        if (plan.getStatus() != null && !plan.getStatus().equals(media.getStatus())) {
            return false;
        }
        if (plan.getMinRating() != null && media.getRating() != null
                && media.getRating().compareTo(BigDecimal.valueOf(plan.getMinRating())) < 0) {
            return false;
        }
        LocalDate releaseDate = media.getReleaseDate();
        if (plan.getYearFrom() != null && releaseDate != null && releaseDate.getYear() < plan.getYearFrom()) {
            return false;
        }
        if (plan.getYearTo() != null && releaseDate != null && releaseDate.getYear() > plan.getYearTo()) {
            return false;
        }
        return true;
    }

    private double score(RagVectorHit hit) {
        return hit.getScore() == null ? 0.0d : hit.getScore();
    }
}
