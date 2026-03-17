package com.jelly.cinema.service.ai.rag;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class RagMetadataRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public RagMetadataRepository(@Qualifier("ragJdbcTemplate") JdbcTemplate jdbcTemplate,
                                 @Qualifier("ragNamedJdbcTemplate") NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public long upsertKnowledgeBase(RagKnowledgeBaseDefinition definition, String embeddingModel, int chunkSize, int chunkOverlap) {
        Long existingId = jdbcTemplate.query(
                "select id from rag_knowledge_base where code = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                definition.getCode()
        );

        if (existingId != null) {
            jdbcTemplate.update("""
                    update rag_knowledge_base
                    set name = ?, domain = ?, status = 'ACTIVE', description = ?, embedding_model = ?, chunk_size = ?, chunk_overlap = ?, update_time = current_timestamp
                    where id = ?
                    """,
                    definition.getName(),
                    definition.getDomain(),
                    definition.getDescription(),
                    embeddingModel,
                    chunkSize,
                    chunkOverlap,
                    existingId
            );
            return existingId;
        }

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("code", definition.getCode())
                .addValue("name", definition.getName())
                .addValue("domain", definition.getDomain())
                .addValue("description", definition.getDescription())
                .addValue("embedding_model", embeddingModel)
                .addValue("chunk_size", chunkSize)
                .addValue("chunk_overlap", chunkOverlap);
        namedParameterJdbcTemplate.update("""
                insert into rag_knowledge_base(code, name, domain, status, description, embedding_model, chunk_size, chunk_overlap)
                values (:code, :name, :domain, 'ACTIVE', :description, :embedding_model, :chunk_size, :chunk_overlap)
                """, parameters, keyHolder, new String[]{"id"});
        return requiredKey(keyHolder);
    }

    public long createTask(Long knowledgeBaseId, String taskType, String scopeKey) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("knowledge_base_id", knowledgeBaseId)
                .addValue("task_type", taskType)
                .addValue("scope_key", scopeKey)
                .addValue("status", "RUNNING");
        namedParameterJdbcTemplate.update("""
                insert into rag_ingest_task(knowledge_base_id, task_type, scope_key, status)
                values (:knowledge_base_id, :task_type, :scope_key, :status)
                """, parameters, keyHolder, new String[]{"id"});
        return requiredKey(keyHolder);
    }

    public void finishTask(long taskId, String status, int sourceCount, int successCount, int failCount, String errorMessage) {
        jdbcTemplate.update("""
                update rag_ingest_task
                set status = ?, source_count = ?, success_count = ?, fail_count = ?, error_message = ?, finish_time = current_timestamp
                where id = ?
                """,
                status,
                sourceCount,
                successCount,
                failCount,
                errorMessage,
                taskId
        );
    }

    public long saveDocument(long knowledgeBaseId, String bizType, long bizId, String title, String sourceType, String sourceUri, String contentHash) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("knowledge_base_id", knowledgeBaseId)
                .addValue("biz_type", bizType)
                .addValue("biz_id", bizId)
                .addValue("title", title)
                .addValue("source_type", sourceType)
                .addValue("source_uri", sourceUri)
                .addValue("content_hash", contentHash);
        namedParameterJdbcTemplate.update("""
                insert into rag_document(knowledge_base_id, biz_type, biz_id, title, source_type, source_uri, content_hash, version_no, status)
                values (:knowledge_base_id, :biz_type, :biz_id, :title, :source_type, :source_uri, :content_hash, 1, 'ACTIVE')
                """, parameters, keyHolder, new String[]{"id"});
        return requiredKey(keyHolder);
    }

    public long saveChunk(long documentId, String knowledgeBaseCode, String bizType, long bizId, String title,
                          int chunkNo, String chunkText, int tokenCount, String metadataJson, String milvusCollection) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("document_id", documentId)
                .addValue("knowledge_base_code", knowledgeBaseCode)
                .addValue("biz_type", bizType)
                .addValue("biz_id", bizId)
                .addValue("title", title)
                .addValue("chunk_no", chunkNo)
                .addValue("chunk_text", chunkText)
                .addValue("token_count", tokenCount)
                .addValue("metadata_json", metadataJson)
                .addValue("milvus_collection", milvusCollection);
        namedParameterJdbcTemplate.update("""
                insert into rag_chunk(document_id, knowledge_base_code, biz_type, biz_id, title, chunk_no, chunk_text, token_count, metadata_json, milvus_collection)
                values (:document_id, :knowledge_base_code, :biz_type, :biz_id, :title, :chunk_no, :chunk_text, :token_count, :metadata_json, :milvus_collection)
                """, parameters, keyHolder, new String[]{"id"});
        return requiredKey(keyHolder);
    }

    public void bindMilvusPrimaryKey(long chunkId, long milvusPrimaryKey) {
        jdbcTemplate.update("update rag_chunk set milvus_primary_key = ? where id = ?", milvusPrimaryKey, chunkId);
    }

    public void deleteByKnowledgeBase(String knowledgeBaseCode) {
        jdbcTemplate.update("delete from rag_document where knowledge_base_id = (select id from rag_knowledge_base where code = ?)", knowledgeBaseCode);
    }

    public void deleteByKnowledgeBaseAndBizId(String knowledgeBaseCode, String bizType, long bizId) {
        jdbcTemplate.update("""
                delete from rag_document
                where knowledge_base_id = (select id from rag_knowledge_base where code = ?)
                  and biz_type = ?
                  and biz_id = ?
                """, knowledgeBaseCode, bizType, bizId);
    }

    public Map<String, Long> countDocumentsByKnowledgeBase() {
        return jdbcTemplate.query("""
                select kb.code as code, count(d.id) as cnt
                from rag_knowledge_base kb
                left join rag_document d on d.knowledge_base_id = kb.id
                group by kb.code
                """, rs -> {
            java.util.LinkedHashMap<String, Long> counts = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                counts.put(rs.getString("code"), rs.getLong("cnt"));
            }
            return counts;
        });
    }

    public Map<String, Long> countChunksByKnowledgeBase() {
        return jdbcTemplate.query("""
                select knowledge_base_code as code, count(*) as cnt
                from rag_chunk
                group by knowledge_base_code
                """, rs -> {
            java.util.LinkedHashMap<String, Long> counts = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                counts.put(rs.getString("code"), rs.getLong("cnt"));
            }
            return counts;
        });
    }

    public Optional<LocalDateTime> latestTaskTime() {
        return Optional.ofNullable(jdbcTemplate.query(
                "select finish_time from rag_ingest_task order by coalesce(finish_time, start_time) desc limit 1",
                rs -> rs.next() ? toLocalDateTime(rs.getTimestamp(1)) : null
        ));
    }

    public long chunkCount() {
        return jdbcTemplate.queryForObject("select count(*) from rag_chunk", Long.class);
    }

    public List<RagChunk> findChunksByBiz(String knowledgeBaseCode, String bizType, long bizId) {
        return jdbcTemplate.query("""
                select id, document_id, knowledge_base_code, biz_type, biz_id, title, chunk_no, chunk_text, token_count, metadata_json, milvus_collection, milvus_primary_key
                from rag_chunk
                where knowledge_base_code = ? and biz_type = ? and biz_id = ?
                order by chunk_no asc
                """, chunkRowMapper(), knowledgeBaseCode, bizType, bizId);
    }

    private RowMapper<RagChunk> chunkRowMapper() {
        return new RowMapper<>() {
            @Override
            public RagChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
                return RagChunk.builder()
                        .id(rs.getLong("id"))
                        .documentId(rs.getLong("document_id"))
                        .knowledgeBaseCode(rs.getString("knowledge_base_code"))
                        .bizType(rs.getString("biz_type"))
                        .bizId(rs.getLong("biz_id"))
                        .title(rs.getString("title"))
                        .chunkNo(rs.getInt("chunk_no"))
                        .chunkText(rs.getString("chunk_text"))
                        .tokenCount(rs.getInt("token_count"))
                        .metadataJson(rs.getString("metadata_json"))
                        .milvusCollection(rs.getString("milvus_collection"))
                        .milvusPrimaryKey(rs.getObject("milvus_primary_key", Long.class))
                        .build();
            }
        };
    }

    private long requiredKey(GeneratedKeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain generated key from PostgreSQL");
        }
        return key.longValue();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
