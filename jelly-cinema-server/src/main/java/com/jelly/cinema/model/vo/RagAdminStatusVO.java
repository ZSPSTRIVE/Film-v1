package com.jelly.cinema.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RagAdminStatusVO {

    private boolean enabled;

    private boolean postgresReady;

    private boolean milvusReady;

    private long mediaCount;

    private long commentCount;

    private LocalDateTime lastTaskTime;

    private List<KnowledgeBaseStatus> knowledgeBases;

    @Data
    public static class KnowledgeBaseStatus {
        private String code;
        private String name;
        private String collectionName;
        private long documentCount;
        private long chunkCount;
    }
}
