package com.jelly.cinema.service.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HybridMediaRetrievalService implements MediaRetrievalService {

    private final ElasticsearchMediaRetrievalService elasticsearchMediaRetrievalService;
    private final MysqlMediaRetrievalService mysqlMediaRetrievalService;

    @Override
    public List<MediaSearchHit> retrieve(AiSearchPlan plan, int size) {
        List<MediaSearchHit> esHits = elasticsearchMediaRetrievalService.retrieve(plan, size);
        if (!esHits.isEmpty()) {
            return esHits;
        }
        return mysqlMediaRetrievalService.retrieve(plan, size);
    }
}
