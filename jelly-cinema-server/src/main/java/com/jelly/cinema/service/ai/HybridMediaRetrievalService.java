package com.jelly.cinema.service.ai;

import com.jelly.cinema.common.config.property.RagProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HybridMediaRetrievalService implements MediaRetrievalService {

    private final ElasticsearchMediaRetrievalService elasticsearchMediaRetrievalService;
    private final VectorMediaRetrievalService vectorMediaRetrievalService;
    private final MysqlMediaRetrievalService mysqlMediaRetrievalService;
    private final RagProperties ragProperties;

    @Override
    public List<MediaSearchHit> retrieve(AiSearchPlan plan, int size) {
        int topK = Math.max(size, ragProperties.getRetrieval().getSearchTopK());
        List<MediaSearchHit> mysqlHits = mysqlMediaRetrievalService.retrieve(plan, topK);
        List<MediaSearchHit> vectorHits = vectorMediaRetrievalService.retrieve(plan, topK);
        List<MediaSearchHit> esHits = elasticsearchMediaRetrievalService.retrieve(plan, topK);

        List<MediaSearchHit> lexicalHits = new ArrayList<>(mysqlHits);
        lexicalHits.addAll(esHits);
        List<MediaSearchHit> merged = mergeHits(lexicalHits, vectorHits, size, plan);
        if (!merged.isEmpty()) {
            return merged;
        }
        return ragProperties.getRetrieval().isFallbackToMysql() ? mysqlHits : List.of();
    }

    private List<MediaSearchHit> mergeHits(List<MediaSearchHit> lexicalHits,
                                           List<MediaSearchHit> vectorHits,
                                           int size,
                                           AiSearchPlan plan) {
        Map<Long, MediaSearchHit> merged = new LinkedHashMap<>();

        double maxLexicalScore = lexicalHits.stream()
                .map(MediaSearchHit::getLexicalScore)
                .filter(java.util.Objects::nonNull)
                .max(Double::compareTo)
                .orElse(1.0d);
        double maxVectorScore = vectorHits.stream()
                .map(MediaSearchHit::getVectorScore)
                .filter(java.util.Objects::nonNull)
                .max(Double::compareTo)
                .orElse(1.0d);

        for (MediaSearchHit hit : lexicalHits) {
            if (hit.getMedia() == null || hit.getMedia().getId() == null) {
                continue;
            }
            MediaSearchHit mergedHit = merged.computeIfAbsent(hit.getMedia().getId(), key -> new MediaSearchHit());
            mergedHit.setMedia(hit.getMedia());
            mergedHit.setHighlight(hit.getHighlight());
            mergedHit.setSource(hit.getSource());
            mergedHit.setLexicalSource(hit.getLexicalSource() == null ? hit.getSource() : hit.getLexicalSource());
            mergedHit.setLexicalScore(normalize(hit.getLexicalScore(), maxLexicalScore));
            mergedHit.setKnowledgeBaseCode(hit.getKnowledgeBaseCode());
        }

        for (MediaSearchHit hit : vectorHits) {
            if (hit.getMedia() == null || hit.getMedia().getId() == null) {
                continue;
            }
            MediaSearchHit mergedHit = merged.computeIfAbsent(hit.getMedia().getId(), key -> new MediaSearchHit());
            if (mergedHit.getMedia() == null) {
                mergedHit.setMedia(hit.getMedia());
            }
            if (!StringUtils.hasText(mergedHit.getHighlight())) {
                mergedHit.setHighlight(hit.getHighlight());
            }
            mergedHit.setVectorScore(normalize(hit.getVectorScore(), maxVectorScore));
            if (!StringUtils.hasText(mergedHit.getKnowledgeBaseCode())) {
                mergedHit.setKnowledgeBaseCode(hit.getKnowledgeBaseCode());
            }
        }

        List<MediaSearchHit> results = new ArrayList<>();
        for (MediaSearchHit hit : merged.values()) {
            if (hit.getMedia() == null) {
                continue;
            }
            double lexicalScore = hit.getLexicalScore() == null ? 0.0d : hit.getLexicalScore();
            double vectorScore = hit.getVectorScore() == null ? 0.0d : hit.getVectorScore();
            double ratingBoost = hit.getMedia().getRating() == null ? 0.0d : hit.getMedia().getRating().doubleValue() / 100.0d;
            double titleBoost = computeTitleBoost(plan, hit);
            double finalScore = lexicalScore * ragProperties.getRetrieval().getLexicalWeight()
                    + vectorScore * ragProperties.getRetrieval().getVectorWeight()
                    + ratingBoost
                    + titleBoost;
            hit.setScore(finalScore);
            hit.setRerankScore(finalScore);
            hit.setSource(resolveSource(hit));
            results.add(hit);
        }

        results.sort(Comparator.comparing(MediaSearchHit::getRerankScore, Comparator.nullsLast(Double::compareTo)).reversed()
                .thenComparing(hit -> hit.getMedia().getRating() == null ? BigDecimal.ZERO : hit.getMedia().getRating(), Comparator.reverseOrder()));
        if (results.size() > size) {
            return results.subList(0, size);
        }
        return results;
    }

    private double computeTitleBoost(AiSearchPlan plan, MediaSearchHit hit) {
        AiQueryMode queryMode = AiSearchQueryHelper.classify(
                plan == null ? null : plan.getSemanticQuery(),
                plan == null ? null : plan.getNormalizedQuery()
        );
        if (queryMode != AiQueryMode.TITLE || plan == null || hit == null || hit.getMedia() == null) {
            return 0.0d;
        }
        String query = plan.getNormalizedQuery();
        if (query == null || query.isBlank()) {
            return 0.0d;
        }
        String normalizedQuery = query.trim().toLowerCase();
        String title = hit.getMedia().getTitle() == null ? "" : hit.getMedia().getTitle().toLowerCase();
        String originalTitle = hit.getMedia().getOriginalTitle() == null ? "" : hit.getMedia().getOriginalTitle().toLowerCase();

        if (title.equals(normalizedQuery) || originalTitle.equals(normalizedQuery)) {
            return 0.85d;
        }
        if (title.contains(normalizedQuery) || originalTitle.contains(normalizedQuery)) {
            return 0.42d;
        }
        return 0.0d;
    }

    private double normalize(Double value, double maxValue) {
        if (value == null || maxValue <= 0) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value / maxValue));
    }

    private String resolveSource(MediaSearchHit hit) {
        boolean hasLexical = hit.getLexicalScore() != null && hit.getLexicalScore() > 0;
        boolean hasVector = hit.getVectorScore() != null && hit.getVectorScore() > 0;
        String lexicalSource = hit.getLexicalSource();
        boolean lexicalExternal = "mysql_external".equals(lexicalSource);
        boolean vectorExternal = "external_media_kb".equals(hit.getKnowledgeBaseCode());

        if (hasLexical && hasVector) {
            if (lexicalExternal || vectorExternal) {
                return "mysql_external_hybrid";
            }
            return "mysql_vector_hybrid";
        }
        if (hasLexical) {
            return StringUtils.hasText(lexicalSource) ? lexicalSource : "mysql";
        }
        if (hasVector) {
            return vectorExternal ? "milvus_external" : "milvus_vector";
        }
        return "unknown";
    }
}
