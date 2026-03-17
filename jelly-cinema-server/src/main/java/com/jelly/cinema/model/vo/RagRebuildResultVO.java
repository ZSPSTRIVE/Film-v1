package com.jelly.cinema.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RagRebuildResultVO {

    private String scope;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private int totalSourceCount;

    private int totalDocumentCount;

    private int totalChunkCount;

    private List<KnowledgeBaseRebuildResult> knowledgeBases;

    @Data
    public static class KnowledgeBaseRebuildResult {
        private String code;
        private String collectionName;
        private int sourceCount;
        private int documentCount;
        private int chunkCount;
        private String status;
    }
}
