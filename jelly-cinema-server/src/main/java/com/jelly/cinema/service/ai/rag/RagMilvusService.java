package com.jelly.cinema.service.ai.rag;

import com.jelly.cinema.common.config.property.RagProperties;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.collection.ShowCollectionsParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.highlevel.dml.InsertRowsParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagMilvusService {

    private static final String VECTOR_FIELD = "vector";

    private final RagProperties ragProperties;
    private final MilvusServiceClient milvusServiceClient;

    public boolean isReady() {
        try {
            R<?> response = milvusServiceClient.showCollections(ShowCollectionsParam.newBuilder().build());
            return response.getStatus() == R.Status.Success.getCode();
        } catch (Exception e) {
            log.warn("Milvus health check failed", e);
            return false;
        }
    }

    public void recreateCollection(String collectionName) {
        if (exists(collectionName)) {
            assertSuccess(milvusServiceClient.dropCollection(
                    DropCollectionParam.newBuilder().withCollectionName(collectionName).build()
            ), "drop collection " + collectionName);
        }
        createCollection(collectionName);
    }

    public void ensureCollection(String collectionName) {
        if (!exists(collectionName)) {
            createCollection(collectionName);
        } else {
            loadCollection(collectionName);
        }
    }

    public void deleteByPrimaryKeys(String collectionName, List<Long> primaryKeys) {
        if (!exists(collectionName) || primaryKeys == null || primaryKeys.isEmpty()) {
            return;
        }

        // Milvus 2.x delete supports primary-key IN expressions only.
        final int batchSize = 200;
        for (int i = 0; i < primaryKeys.size(); i += batchSize) {
            List<Long> batch = primaryKeys.subList(i, Math.min(i + batchSize, primaryKeys.size()));
            String expr = "id in [" + batch.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "]";
            assertSuccess(milvusServiceClient.delete(DeleteParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .build()), "delete vectors from " + collectionName);
        }
    }

    public void insert(String collectionName, List<RagChunk> chunks, List<List<Float>> vectors) {
        if (chunks.isEmpty()) {
            return;
        }
        ensureCollection(collectionName);

        List<JsonObject> rows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            RagChunk chunk = chunks.get(i);
            JsonObject row = new JsonObject();
            row.addProperty("id", chunk.getMilvusPrimaryKey());
            row.addProperty("biz_id", chunk.getBizId());
            row.addProperty("media_title", chunk.getTitle());
            row.addProperty("chunk_text", chunk.getChunkText());
            JsonArray vector = new JsonArray();
            for (Float value : vectors.get(i)) {
                vector.add(value);
            }
            row.add(VECTOR_FIELD, vector);
            rows.add(row);
        }

        assertSuccess(milvusServiceClient.insert(InsertRowsParam.newBuilder()
                .withCollectionName(collectionName)
                .withRows(rows)
                .build()), "insert vectors into " + collectionName);

        assertSuccess(milvusServiceClient.flush(FlushParam.newBuilder()
                .addCollectionName(collectionName)
                .withSyncFlush(true)
            .withSyncFlushWaitingTimeout(timeoutSeconds())
            .withSyncFlushWaitingInterval(1L)
                .build()), "flush collection " + collectionName);
        loadCollection(collectionName);
    }

    public List<RagVectorHit> search(String collectionName, String knowledgeBaseCode, List<Float> queryVector, String expr, int topK) {
        if (!exists(collectionName) || queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }

        SearchParam.Builder builder = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withVectorFieldName(VECTOR_FIELD)
                .withMetricType(MetricType.IP)
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .withVectors(List.of(queryVector))
                .withTopK(topK)
                .withParams("{}")
                .withOutFields(List.of("biz_id", "media_title", "chunk_text"));

        if (expr != null && !expr.isBlank()) {
            builder.withExpr(expr);
        }

        R<io.milvus.grpc.SearchResults> response = milvusServiceClient.search(builder.build());
        assertSuccess(response, "search collection " + collectionName);

        SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
        List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);
        List<RagVectorHit> hits = new ArrayList<>(idScores.size());
        for (SearchResultsWrapper.IDScore idScore : idScores) {
            hits.add(RagVectorHit.builder()
                    .chunkId(idScore.getLongID())
                    .bizId(toLong(idScore.getFieldValues().get("biz_id")))
                    .title(stringValue(idScore.getFieldValues().get("media_title")))
                    .chunkText(stringValue(idScore.getFieldValues().get("chunk_text")))
                    .collectionName(collectionName)
                    .knowledgeBaseCode(knowledgeBaseCode)
                    .score((double) idScore.getScore())
                    .build());
        }
        return hits;
    }

    private void createCollection(String collectionName) {
        List<FieldType> fieldTypes = List.of(
                FieldType.newBuilder()
                        .withName("id")
                        .withDataType(DataType.Int64)
                        .withPrimaryKey(true)
                        .withAutoID(false)
                        .build(),
                FieldType.newBuilder()
                        .withName("biz_id")
                        .withDataType(DataType.Int64)
                        .build(),
                FieldType.newBuilder()
                        .withName("media_title")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(256)
                        .build(),
                FieldType.newBuilder()
                        .withName("chunk_text")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(4096)
                        .build(),
                FieldType.newBuilder()
                        .withName(VECTOR_FIELD)
                        .withDataType(DataType.FloatVector)
                        .withDimension(ragProperties.getEmbedding().getDimension())
                        .build()
        );

        assertSuccess(milvusServiceClient.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("Jelly Cinema RAG collection: " + collectionName)
                .withFieldTypes(fieldTypes)
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .withEnableDynamicField(false)
                .build()), "create collection " + collectionName);

        assertSuccess(milvusServiceClient.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(VECTOR_FIELD)
                .withIndexName("idx_" + collectionName + "_vector")
                .withIndexType(IndexType.FLAT)
                .withMetricType(MetricType.IP)
                .withExtraParam("{}")
                .withSyncMode(true)
            .withSyncWaitingTimeout(timeoutSeconds())
            .withSyncWaitingInterval(1L)
                .build()), "create index for " + collectionName);

        loadCollection(collectionName);
    }

    private void loadCollection(String collectionName) {
        assertSuccess(milvusServiceClient.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withSyncLoad(true)
                .withSyncLoadWaitingTimeout(timeoutSeconds())
                .withSyncLoadWaitingInterval(1L)
                .build()), "load collection " + collectionName);
    }

    private long timeoutSeconds() {
        long seconds = ragProperties.getMilvus().getLoadTimeoutMs() / 1000L;
        if (seconds <= 0) {
            return 1L;
        }
        return Math.min(seconds, 300L);
    }

    private boolean exists(String collectionName) {
        try {
            R<Boolean> response = milvusServiceClient.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build());
            return response.getStatus() == R.Status.Success.getCode() && Boolean.TRUE.equals(response.getData());
        } catch (Exception e) {
            log.warn("Milvus hasCollection failed for {}", collectionName, e);
            return false;
        }
    }

    private void assertSuccess(R<?> response, String action) {
        if (response == null || response.getStatus() != R.Status.Success.getCode()) {
            String message = response == null ? "no response" : response.getMessage();
            throw new IllegalStateException("Milvus action failed: " + action + ", message=" + message);
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
