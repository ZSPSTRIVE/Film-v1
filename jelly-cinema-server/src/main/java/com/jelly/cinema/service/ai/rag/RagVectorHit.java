package com.jelly.cinema.service.ai.rag;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RagVectorHit {

    private Long chunkId;

    private Long bizId;

    private String title;

    private String chunkText;

    private String collectionName;

    private String knowledgeBaseCode;

    private Double score;
}
