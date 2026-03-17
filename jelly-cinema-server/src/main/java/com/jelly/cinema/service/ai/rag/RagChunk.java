package com.jelly.cinema.service.ai.rag;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RagChunk {

    private Long id;

    private Long documentId;

    private String knowledgeBaseCode;

    private String bizType;

    private Long bizId;

    private String title;

    private Integer chunkNo;

    private String chunkText;

    private Integer tokenCount;

    private String metadataJson;

    private String milvusCollection;

    private Long milvusPrimaryKey;
}
