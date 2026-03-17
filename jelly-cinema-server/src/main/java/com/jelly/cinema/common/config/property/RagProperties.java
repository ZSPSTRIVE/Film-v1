package com.jelly.cinema.common.config.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private boolean enable = true;

    private boolean autoBootstrap = true;

    private boolean rebuildOnStartup = false;

    private int bootstrapBatchSize = 100;

    private Postgres postgres = new Postgres();

    private Milvus milvus = new Milvus();

    private Embedding embedding = new Embedding();

    private Chunking chunking = new Chunking();

    private Retrieval retrieval = new Retrieval();

    private Collections collections = new Collections();

    @Data
    public static class Postgres {
        private boolean enable = true;
        private String url = "jdbc:postgresql://127.0.0.1:5432/jelly_cinema_rag";
        private String username = "postgres";
        private String password = "AiInfra@123";
        private String driverClassName = "org.postgresql.Driver";
    }

    @Data
    public static class Milvus {
        private boolean enable = true;
        private String host = "127.0.0.1";
        private int port = 19530;
        private String databaseName = "default";
        private String username;
        private String password;
        private long connectTimeoutMs = 5000L;
        private long rpcDeadlineMs = 30000L;
        private long loadTimeoutMs = 30000L;
    }

    @Data
    public static class Embedding {
        private String provider = "hashing";
        private int dimension = 256;
    }

    @Data
    public static class Chunking {
        private int chunkSize = 260;
        private int chunkOverlap = 40;
        private int maxCommentChunksPerMedia = 20;
    }

    @Data
    public static class Retrieval {
        private int searchTopK = 8;
        private int qaTopK = 6;
        private double lexicalWeight = 0.58d;
        private double vectorWeight = 0.42d;
        private int maxCitations = 5;
        private boolean fallbackToMysql = true;
    }

    @Data
    public static class Collections {
        private String mediaProfile = "rag_media_profile_chunk_v1";
        private String commentQa = "rag_comment_chunk_v1";
        private String externalMedia = "rag_external_media_chunk_v1";
    }
}
