package com.jelly.cinema.service.ai;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jelly.cinema.common.config.property.ElasticsearchProperties;
import com.jelly.cinema.mapper.MediaMapper;
import com.jelly.cinema.model.entity.Media;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchMediaRetrievalService implements MediaRetrievalService {

    private final ElasticsearchProperties elasticsearchProperties;
    private final MediaMapper mediaMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<MediaSearchHit> retrieve(AiSearchPlan plan, int size) {
        if (!elasticsearchProperties.isEnable()) {
            return Collections.emptyList();
        }

        try {
            RestClient.Builder builder = RestClient.builder().baseUrl(elasticsearchProperties.getBaseUrl());
            if (StringUtils.hasText(elasticsearchProperties.getUsername())) {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, basicAuthHeader(
                        elasticsearchProperties.getUsername(),
                        elasticsearchProperties.getPassword()
                ));
            }

            JsonNode response = builder.build()
                    .post()
                    .uri("/{index}/_search", elasticsearchProperties.getMediaIndex())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildSearchBody(plan, size))
                    .retrieve()
                    .body(JsonNode.class);

            return toHits(response);
        } catch (Exception e) {
            log.warn("Elasticsearch retrieval failed, fallback to MySQL. plan={}", plan, e);
            return Collections.emptyList();
        }
    }

    private Map<String, Object> buildSearchBody(AiSearchPlan plan, int size) {
        Map<String, Object> body = new HashMap<>();
        body.put("size", Math.max(1, size));
        body.put("_source", List.of("id"));

        List<Object> must = new ArrayList<>();
        if (StringUtils.hasText(plan.getNormalizedQuery())) {
            must.add(Map.of("multi_match", Map.of(
                    "query", plan.getNormalizedQuery(),
                    "fields", List.of("title^4", "originalTitle^2", "summary^1.5"),
                    "type", "best_fields",
                    "operator", "and"
            )));
        } else {
            must.add(Map.of("match_all", Map.of()));
        }

        List<Object> filter = new ArrayList<>();
        if (plan.getType() != null && plan.getType() > 0) {
            filter.add(Map.of("term", Map.of("type", plan.getType())));
        }
        if (plan.getStatus() != null) {
            filter.add(Map.of("term", Map.of("status", plan.getStatus())));
        }

        body.put("query", Map.of("bool", Map.of(
                "must", must,
                "filter", filter
        )));
        body.put("highlight", Map.of("fields", Map.of(
                "title", Map.of(),
                "summary", Map.of()
        )));
        body.put("sort", buildSort(plan));
        return body;
    }

    private List<Object> buildSort(AiSearchPlan plan) {
        if ("releaseDate".equalsIgnoreCase(plan.getSortBy())) {
            return List.of(
                    Map.of("releaseDate", Map.of("order", "desc")),
                    Map.of("_score", Map.of("order", "desc"))
            );
        }
        return List.of(
                Map.of("_score", Map.of("order", "desc")),
                Map.of("rating", Map.of("order", "desc"))
        );
    }

    private List<MediaSearchHit> toHits(JsonNode response) {
        JsonNode hitNodes = response.path("hits").path("hits");
        if (!hitNodes.isArray() || hitNodes.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Map<String, Object>> metaMap = new LinkedHashMap<>();
        for (JsonNode hitNode : hitNodes) {
            Long mediaId = hitNode.path("_source").path("id").asLong(hitNode.path("_id").asLong());
            if (mediaId == null || mediaId <= 0) {
                continue;
            }
            Map<String, Object> meta = new HashMap<>();
            meta.put("score", hitNode.path("_score").isNumber() ? hitNode.path("_score").asDouble() : null);
            JsonNode highlights = hitNode.path("highlight");
            if (highlights.has("summary")) {
                meta.put("highlight", highlights.path("summary").get(0).asText());
            } else if (highlights.has("title")) {
                meta.put("highlight", highlights.path("title").get(0).asText());
            }
            metaMap.put(mediaId, meta);
        }

        if (CollectionUtils.isEmpty(metaMap)) {
            return Collections.emptyList();
        }

        Map<Long, Media> mediaMap = mediaMapper.selectByIds(new ArrayList<>(metaMap.keySet())).stream()
                .collect(Collectors.toMap(Media::getId, media -> media));

        List<MediaSearchHit> hits = new ArrayList<>();
        metaMap.forEach((mediaId, meta) -> {
            Media media = mediaMap.get(mediaId);
            if (media != null) {
                Double score = meta.get("score") instanceof Double value ? value : null;
                String highlight = meta.get("highlight") instanceof String value ? value : null;
                hits.add(new MediaSearchHit(media, score, highlight, "elasticsearch_bm25"));
            }
        });
        return hits;
    }

    private String basicAuthHeader(String username, String password) {
        String raw = username + ":" + (password == null ? "" : password);
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
