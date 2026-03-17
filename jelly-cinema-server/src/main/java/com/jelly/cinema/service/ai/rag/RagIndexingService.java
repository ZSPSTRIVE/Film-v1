package com.jelly.cinema.service.ai.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jelly.cinema.common.config.property.RagProperties;
import com.jelly.cinema.common.exception.BusinessException;
import com.jelly.cinema.entity.Comment;
import com.jelly.cinema.mapper.CommentMapper;
import com.jelly.cinema.mapper.MediaExternalResourceMapper;
import com.jelly.cinema.mapper.MediaMapper;
import com.jelly.cinema.model.entity.Media;
import com.jelly.cinema.model.entity.MediaExternalResource;
import com.jelly.cinema.model.vo.RagAdminStatusVO;
import com.jelly.cinema.model.vo.RagRebuildResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagIndexingService {

    private final RagProperties ragProperties;
    private final MediaMapper mediaMapper;
    private final CommentMapper commentMapper;
    private final MediaExternalResourceMapper mediaExternalResourceMapper;
    private final RagMetadataRepository ragMetadataRepository;
    private final RagChunkingService ragChunkingService;
    private final RagEmbeddingService ragEmbeddingService;
    private final RagMilvusService ragMilvusService;
    private final ObjectMapper objectMapper;

    public void bootstrapIfNeeded() {
        if (!ragProperties.isEnable() || !ragProperties.isAutoBootstrap()) {
            return;
        }
        if (ragProperties.isRebuildOnStartup() || ragMetadataRepository.chunkCount() == 0) {
            rebuildAll();
        }
    }

    public RagRebuildResultVO rebuildAll() {
        assertEnabled();
        LocalDateTime startedAt = LocalDateTime.now();
        RagRebuildResultVO result = new RagRebuildResultVO();
        result.setScope("FULL");
        result.setStartedAt(startedAt);

        List<RagRebuildResultVO.KnowledgeBaseRebuildResult> items = new ArrayList<>();
        items.add(rebuildMediaProfileKnowledgeBase(loadActiveMedia()));
        items.add(rebuildCommentKnowledgeBase(loadQualifiedComments()));
        items.add(rebuildExternalMediaKnowledgeBase(loadActiveExternalResources()));

        fillRebuildSummary(result, items);
        return result;
    }

    public RagRebuildResultVO rebuildMedia(Long mediaId) {
        assertEnabled();
        Media media = mediaMapper.selectById(mediaId);
        if (media == null || media.getDeleted() == 1) {
            throw new BusinessException(404, "Media not found");
        }

        LocalDateTime startedAt = LocalDateTime.now();
        RagRebuildResultVO result = new RagRebuildResultVO();
        result.setScope("MEDIA:" + mediaId);
        result.setStartedAt(startedAt);

        clearKnowledgeBasesForMediaIds(Set.of(mediaId));

        List<RagRebuildResultVO.KnowledgeBaseRebuildResult> items = new ArrayList<>();
        items.add(upsertMediaProfileKnowledgeBase(List.of(media)));
        items.add(upsertCommentKnowledgeBase(loadQualifiedCommentsByMediaIds(Set.of(mediaId))));
        items.add(upsertExternalMediaKnowledgeBase(loadExternalResourcesByMediaIds(Set.of(mediaId))));

        fillRebuildSummary(result, items);
        return result;
    }

    public void rebuildMediaIndexes(Set<Long> mediaIds) {
        assertEnabled();
        Set<Long> effectiveIds = mediaIds == null ? Set.of() : mediaIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (effectiveIds.isEmpty()) {
            return;
        }

        clearKnowledgeBasesForMediaIds(effectiveIds);
        upsertMediaProfileKnowledgeBase(loadMediaByIds(effectiveIds));
        upsertCommentKnowledgeBase(loadQualifiedCommentsByMediaIds(effectiveIds));
        upsertExternalMediaKnowledgeBase(loadExternalResourcesByMediaIds(effectiveIds));
    }

    public RagAdminStatusVO status() {
        RagAdminStatusVO status = new RagAdminStatusVO();
        status.setEnabled(ragProperties.isEnable());
        status.setMediaCount(mediaMapper.selectCount(new LambdaQueryWrapper<Media>().eq(Media::getDeleted, 0)));
        status.setCommentCount(commentMapper.selectCount(new LambdaQueryWrapper<Comment>().isNotNull(Comment::getContent)));
        status.setPostgresReady(checkPostgresReady());
        status.setMilvusReady(ragMilvusService.isReady());
        status.setLastTaskTime(ragMetadataRepository.latestTaskTime().orElse(null));

        Map<String, Long> documentCounts = ragMetadataRepository.countDocumentsByKnowledgeBase();
        Map<String, Long> chunkCounts = ragMetadataRepository.countChunksByKnowledgeBase();
        List<RagAdminStatusVO.KnowledgeBaseStatus> knowledgeBases = new ArrayList<>();
        for (RagKnowledgeBaseDefinition definition : RagKnowledgeBaseDefinition.values()) {
            RagAdminStatusVO.KnowledgeBaseStatus item = new RagAdminStatusVO.KnowledgeBaseStatus();
            item.setCode(definition.getCode());
            item.setName(definition.getName());
            item.setCollectionName(definition.collectionName(ragProperties));
            item.setDocumentCount(documentCounts.getOrDefault(definition.getCode(), 0L));
            item.setChunkCount(chunkCounts.getOrDefault(definition.getCode(), 0L));
            knowledgeBases.add(item);
        }
        status.setKnowledgeBases(knowledgeBases);
        return status;
    }

    private void fillRebuildSummary(RagRebuildResultVO result,
                                    List<RagRebuildResultVO.KnowledgeBaseRebuildResult> items) {
        result.setKnowledgeBases(items);
        result.setFinishedAt(LocalDateTime.now());
        result.setTotalSourceCount(items.stream().mapToInt(RagRebuildResultVO.KnowledgeBaseRebuildResult::getSourceCount).sum());
        result.setTotalDocumentCount(items.stream().mapToInt(RagRebuildResultVO.KnowledgeBaseRebuildResult::getDocumentCount).sum());
        result.setTotalChunkCount(items.stream().mapToInt(RagRebuildResultVO.KnowledgeBaseRebuildResult::getChunkCount).sum());
    }

    private void clearKnowledgeBasesForMediaIds(Set<Long> mediaIds) {
        for (Long mediaId : mediaIds) {
            deleteVectorsByMetadata(RagKnowledgeBaseDefinition.MEDIA_PROFILE, mediaId);
            deleteVectorsByMetadata(RagKnowledgeBaseDefinition.COMMENT_QA, mediaId);
            deleteVectorsByMetadata(RagKnowledgeBaseDefinition.EXTERNAL_MEDIA, mediaId);
            ragMetadataRepository.deleteByKnowledgeBaseAndBizId(
                    RagKnowledgeBaseDefinition.MEDIA_PROFILE.getCode(),
                    RagKnowledgeBaseDefinition.MEDIA_PROFILE.getBizType(),
                    mediaId
            );
            ragMetadataRepository.deleteByKnowledgeBaseAndBizId(
                    RagKnowledgeBaseDefinition.COMMENT_QA.getCode(),
                    RagKnowledgeBaseDefinition.COMMENT_QA.getBizType(),
                    mediaId
            );
            ragMetadataRepository.deleteByKnowledgeBaseAndBizId(
                    RagKnowledgeBaseDefinition.EXTERNAL_MEDIA.getCode(),
                    RagKnowledgeBaseDefinition.EXTERNAL_MEDIA.getBizType(),
                    mediaId
            );
        }
    }

    private void deleteVectorsByMetadata(RagKnowledgeBaseDefinition definition, Long mediaId) {
        List<Long> primaryKeys = ragMetadataRepository.findChunksByBiz(
                        definition.getCode(),
                        definition.getBizType(),
                        mediaId
                ).stream()
                .map(chunk -> chunk.getMilvusPrimaryKey() != null ? chunk.getMilvusPrimaryKey() : chunk.getId())
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .toList();
        ragMilvusService.deleteByPrimaryKeys(definition.collectionName(ragProperties), primaryKeys);
    }

    private RagRebuildResultVO.KnowledgeBaseRebuildResult rebuildMediaProfileKnowledgeBase(List<Media> mediaList) {
        return indexMediaProfileKnowledgeBase(mediaList, true);
    }

    private RagRebuildResultVO.KnowledgeBaseRebuildResult upsertMediaProfileKnowledgeBase(List<Media> mediaList) {
        return indexMediaProfileKnowledgeBase(mediaList, false);
    }

    private RagRebuildResultVO.KnowledgeBaseRebuildResult indexMediaProfileKnowledgeBase(List<Media> mediaList, boolean fullRebuild) {
        RagKnowledgeBaseDefinition definition = RagKnowledgeBaseDefinition.MEDIA_PROFILE;
        if (fullRebuild) {
            ragMilvusService.recreateCollection(definition.collectionName(ragProperties));
            ragMetadataRepository.deleteByKnowledgeBase(definition.getCode());
        }

        long knowledgeBaseId = ragMetadataRepository.upsertKnowledgeBase(
                definition,
                ragProperties.getEmbedding().getProvider(),
                ragProperties.getChunking().getChunkSize(),
                ragProperties.getChunking().getChunkOverlap()
        );
        long taskId = ragMetadataRepository.createTask(
                knowledgeBaseId,
                fullRebuild ? "FULL_REBUILD" : "PARTIAL_REBUILD",
                fullRebuild ? definition.getCode() : definition.getCode() + ":PARTIAL"
        );

        RagRebuildResultVO.KnowledgeBaseRebuildResult result = new RagRebuildResultVO.KnowledgeBaseRebuildResult();
        result.setCode(definition.getCode());
        result.setCollectionName(definition.collectionName(ragProperties));
        result.setSourceCount(mediaList.size());

        int documentCount = 0;
        int chunkCount = 0;
        try {
            List<RagChunk> batch = new ArrayList<>();
            for (Media media : mediaList) {
                String content = buildMediaProfileContent(media);
                List<String> chunks = ragChunkingService.split(content);
                if (chunks.isEmpty()) {
                    continue;
                }

                long documentId = ragMetadataRepository.saveDocument(
                        knowledgeBaseId,
                        definition.getBizType(),
                        media.getId(),
                        media.getTitle(),
                        "media",
                        "media:" + media.getId(),
                        md5(content)
                );
                documentCount++;

                for (int index = 0; index < chunks.size(); index++) {
                    String chunkText = chunks.get(index);
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("mediaId", media.getId());
                    metadata.put("type", media.getType());
                    metadata.put("status", media.getStatus());
                    metadata.put("rating", media.getRating());
                    long chunkId = ragMetadataRepository.saveChunk(
                            documentId,
                            definition.getCode(),
                            definition.getBizType(),
                            media.getId(),
                            media.getTitle(),
                            index,
                            chunkText,
                            estimateTokens(chunkText),
                            objectMapper.writeValueAsString(metadata),
                            definition.collectionName(ragProperties)
                    );
                    ragMetadataRepository.bindMilvusPrimaryKey(chunkId, chunkId);
                    batch.add(RagChunk.builder()
                            .id(chunkId)
                            .documentId(documentId)
                            .knowledgeBaseCode(definition.getCode())
                            .bizType(definition.getBizType())
                            .bizId(media.getId())
                            .title(media.getTitle())
                            .chunkNo(index)
                            .chunkText(chunkText)
                            .tokenCount(estimateTokens(chunkText))
                            .metadataJson(objectMapper.writeValueAsString(metadata))
                            .milvusCollection(definition.collectionName(ragProperties))
                            .milvusPrimaryKey(chunkId)
                            .build());
                    chunkCount++;
                }
                flushBatch(definition, batch);
            }
            flushRemaining(definition, batch);
            ragMetadataRepository.finishTask(taskId, "SUCCESS", mediaList.size(), documentCount, 0, null);
            result.setStatus("SUCCESS");
        } catch (Exception e) {
            ragMetadataRepository.finishTask(taskId, "FAILED", mediaList.size(), documentCount, mediaList.size() - documentCount, e.getMessage());
            result.setStatus("FAILED");
            throw new BusinessException(500, "Failed to rebuild media profile knowledge base: " + e.getMessage());
        }

        result.setDocumentCount(documentCount);
        result.setChunkCount(chunkCount);
        return result;
    }

    private RagRebuildResultVO.KnowledgeBaseRebuildResult rebuildCommentKnowledgeBase(List<Comment> comments) {
        return indexCommentKnowledgeBase(comments, true);
    }

    private RagRebuildResultVO.KnowledgeBaseRebuildResult upsertCommentKnowledgeBase(List<Comment> comments) {
        return indexCommentKnowledgeBase(comments, false);
    }

    private RagRebuildResultVO.KnowledgeBaseRebuildResult indexCommentKnowledgeBase(List<Comment> comments, boolean fullRebuild) {
        RagKnowledgeBaseDefinition definition = RagKnowledgeBaseDefinition.COMMENT_QA;
        if (fullRebuild) {
            ragMilvusService.recreateCollection(definition.collectionName(ragProperties));
            ragMetadataRepository.deleteByKnowledgeBase(definition.getCode());
        }

        long knowledgeBaseId = ragMetadataRepository.upsertKnowledgeBase(
                definition,
                ragProperties.getEmbedding().getProvider(),
                ragProperties.getChunking().getChunkSize(),
                ragProperties.getChunking().getChunkOverlap()
        );
        long taskId = ragMetadataRepository.createTask(
                knowledgeBaseId,
                fullRebuild ? "FULL_REBUILD" : "PARTIAL_REBUILD",
                fullRebuild ? definition.getCode() : definition.getCode() + ":PARTIAL"
        );

        RagRebuildResultVO.KnowledgeBaseRebuildResult result = new RagRebuildResultVO.KnowledgeBaseRebuildResult();
        result.setCode(definition.getCode());
        result.setCollectionName(definition.collectionName(ragProperties));
        result.setSourceCount(comments.size());

        int documentCount = 0;
        int chunkCount = 0;
        try {
            Map<Long, Media> mediaMap = loadActiveMedia().stream()
                    .collect(java.util.stream.Collectors.toMap(Media::getId, media -> media));
            List<Comment> orderedComments = comments.stream()
                    .filter(comment -> StringUtils.hasText(comment.getContent()))
                    .sorted(Comparator.comparing(Comment::getLikeCount, Comparator.nullsLast(Integer::compareTo)).reversed()
                            .thenComparing(Comment::getCreateTime, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();

            Map<Long, Integer> perMediaCounter = new LinkedHashMap<>();
            List<RagChunk> batch = new ArrayList<>();
            for (Comment comment : orderedComments) {
                Media media = mediaMap.get(comment.getMediaId());
                if (media == null) {
                    continue;
                }

                int accepted = perMediaCounter.getOrDefault(comment.getMediaId(), 0);
                if (accepted >= ragProperties.getChunking().getMaxCommentChunksPerMedia()) {
                    continue;
                }

                String content = buildCommentContent(media, comment);
                List<String> chunks = ragChunkingService.split(content);
                if (chunks.isEmpty()) {
                    continue;
                }

                long documentId = ragMetadataRepository.saveDocument(
                        knowledgeBaseId,
                        definition.getBizType(),
                        media.getId(),
                        media.getTitle(),
                        "comment",
                        "comment:" + comment.getId(),
                        md5(content)
                );
                documentCount++;
                perMediaCounter.put(comment.getMediaId(), accepted + 1);

                for (int index = 0; index < chunks.size(); index++) {
                    String chunkText = chunks.get(index);
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("mediaId", media.getId());
                    metadata.put("commentId", comment.getId());
                    metadata.put("commentRating", comment.getRating());
                    metadata.put("likeCount", comment.getLikeCount());
                    long chunkId = ragMetadataRepository.saveChunk(
                            documentId,
                            definition.getCode(),
                            definition.getBizType(),
                            media.getId(),
                            media.getTitle(),
                            index,
                            chunkText,
                            estimateTokens(chunkText),
                            objectMapper.writeValueAsString(metadata),
                            definition.collectionName(ragProperties)
                    );
                    ragMetadataRepository.bindMilvusPrimaryKey(chunkId, chunkId);
                    batch.add(RagChunk.builder()
                            .id(chunkId)
                            .documentId(documentId)
                            .knowledgeBaseCode(definition.getCode())
                            .bizType(definition.getBizType())
                            .bizId(media.getId())
                            .title(media.getTitle())
                            .chunkNo(index)
                            .chunkText(chunkText)
                            .tokenCount(estimateTokens(chunkText))
                            .metadataJson(objectMapper.writeValueAsString(metadata))
                            .milvusCollection(definition.collectionName(ragProperties))
                            .milvusPrimaryKey(chunkId)
                            .build());
                    chunkCount++;
                }
                flushBatch(definition, batch);
            }
            flushRemaining(definition, batch);
            ragMetadataRepository.finishTask(taskId, "SUCCESS", comments.size(), documentCount, 0, null);
            result.setStatus("SUCCESS");
        } catch (Exception e) {
            ragMetadataRepository.finishTask(taskId, "FAILED", comments.size(), documentCount, comments.size() - documentCount, e.getMessage());
            result.setStatus("FAILED");
            throw new BusinessException(500, "Failed to rebuild comment knowledge base: " + e.getMessage());
        }

        result.setDocumentCount(documentCount);
        result.setChunkCount(chunkCount);
        return result;
    }

    private RagRebuildResultVO.KnowledgeBaseRebuildResult rebuildExternalMediaKnowledgeBase(List<MediaExternalResource> resources) {
        return indexExternalMediaKnowledgeBase(resources, true);
    }

    private RagRebuildResultVO.KnowledgeBaseRebuildResult upsertExternalMediaKnowledgeBase(List<MediaExternalResource> resources) {
        return indexExternalMediaKnowledgeBase(resources, false);
    }

    private RagRebuildResultVO.KnowledgeBaseRebuildResult indexExternalMediaKnowledgeBase(List<MediaExternalResource> resources,
                                                                                           boolean fullRebuild) {
        RagKnowledgeBaseDefinition definition = RagKnowledgeBaseDefinition.EXTERNAL_MEDIA;
        if (fullRebuild) {
            ragMilvusService.recreateCollection(definition.collectionName(ragProperties));
            ragMetadataRepository.deleteByKnowledgeBase(definition.getCode());
        }

        long knowledgeBaseId = ragMetadataRepository.upsertKnowledgeBase(
                definition,
                ragProperties.getEmbedding().getProvider(),
                ragProperties.getChunking().getChunkSize(),
                ragProperties.getChunking().getChunkOverlap()
        );
        long taskId = ragMetadataRepository.createTask(
                knowledgeBaseId,
                fullRebuild ? "FULL_REBUILD" : "PARTIAL_REBUILD",
                fullRebuild ? definition.getCode() : definition.getCode() + ":PARTIAL"
        );

        RagRebuildResultVO.KnowledgeBaseRebuildResult result = new RagRebuildResultVO.KnowledgeBaseRebuildResult();
        result.setCode(definition.getCode());
        result.setCollectionName(definition.collectionName(ragProperties));
        result.setSourceCount(resources.size());

        int documentCount = 0;
        int chunkCount = 0;
        try {
            List<RagChunk> batch = new ArrayList<>();
            for (MediaExternalResource resource : resources) {
                if (resource.getMediaId() == null) {
                    continue;
                }
                String content = buildExternalMediaContent(resource);
                List<String> chunks = ragChunkingService.split(content);
                if (chunks.isEmpty()) {
                    continue;
                }

                String title = StringUtils.hasText(resource.getCleanTitle()) ? resource.getCleanTitle() : resource.getRawTitle();
                long documentId = ragMetadataRepository.saveDocument(
                        knowledgeBaseId,
                        definition.getBizType(),
                        resource.getMediaId(),
                        title,
                        "external_media",
                        resource.getProviderName() + ":" + resource.getExternalItemId(),
                        md5(content)
                );
                documentCount++;

                for (int index = 0; index < chunks.size(); index++) {
                    String chunkText = chunks.get(index);
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("mediaId", resource.getMediaId());
                    metadata.put("providerName", resource.getProviderName());
                    metadata.put("sourceKey", resource.getSourceKey());
                    metadata.put("releaseYear", resource.getReleaseYear());
                    metadata.put("rating", resource.getRating());
                    long chunkId = ragMetadataRepository.saveChunk(
                            documentId,
                            definition.getCode(),
                            definition.getBizType(),
                            resource.getMediaId(),
                            title,
                            index,
                            chunkText,
                            estimateTokens(chunkText),
                            objectMapper.writeValueAsString(metadata),
                            definition.collectionName(ragProperties)
                    );
                    ragMetadataRepository.bindMilvusPrimaryKey(chunkId, chunkId);
                    batch.add(RagChunk.builder()
                            .id(chunkId)
                            .documentId(documentId)
                            .knowledgeBaseCode(definition.getCode())
                            .bizType(definition.getBizType())
                            .bizId(resource.getMediaId())
                            .title(title)
                            .chunkNo(index)
                            .chunkText(chunkText)
                            .tokenCount(estimateTokens(chunkText))
                            .metadataJson(objectMapper.writeValueAsString(metadata))
                            .milvusCollection(definition.collectionName(ragProperties))
                            .milvusPrimaryKey(chunkId)
                            .build());
                    chunkCount++;
                }
                flushBatch(definition, batch);
            }
            flushRemaining(definition, batch);
            ragMetadataRepository.finishTask(taskId, "SUCCESS", resources.size(), documentCount, 0, null);
            result.setStatus("SUCCESS");
        } catch (Exception e) {
            ragMetadataRepository.finishTask(taskId, "FAILED", resources.size(), documentCount, resources.size() - documentCount, e.getMessage());
            result.setStatus("FAILED");
            throw new BusinessException(500, "Failed to rebuild external media knowledge base: " + e.getMessage());
        }

        result.setDocumentCount(documentCount);
        result.setChunkCount(chunkCount);
        return result;
    }

    private void flushBatch(RagKnowledgeBaseDefinition definition, List<RagChunk> batch) {
        if (batch.size() < ragProperties.getBootstrapBatchSize()) {
            return;
        }
        flushRemaining(definition, batch);
    }

    private void flushRemaining(RagKnowledgeBaseDefinition definition, List<RagChunk> batch) {
        if (batch.isEmpty()) {
            return;
        }
        List<List<Float>> vectors = ragEmbeddingService.embedAll(batch.stream().map(RagChunk::getChunkText).toList());
        ragMilvusService.insert(definition.collectionName(ragProperties), List.copyOf(batch), vectors);
        batch.clear();
    }

    private List<Media> loadActiveMedia() {
        return mediaMapper.selectList(new LambdaQueryWrapper<Media>()
                .eq(Media::getDeleted, 0)
                .orderByDesc(Media::getRating, Media::getReleaseDate));
    }

    private List<Media> loadMediaByIds(Set<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return List.of();
        }
        return mediaMapper.selectList(new LambdaQueryWrapper<Media>()
                .eq(Media::getDeleted, 0)
                .in(Media::getId, mediaIds)
                .orderByDesc(Media::getRating, Media::getReleaseDate));
    }

    private List<Comment> loadQualifiedComments() {
        return commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .isNotNull(Comment::getContent)
                .eq(Comment::getAuditStatus, 1)
                .orderByDesc(Comment::getLikeCount, Comment::getCreateTime));
    }

    private List<Comment> loadQualifiedCommentsByMediaIds(Set<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return List.of();
        }
        return commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .isNotNull(Comment::getContent)
                .eq(Comment::getAuditStatus, 1)
                .in(Comment::getMediaId, mediaIds)
                .orderByDesc(Comment::getLikeCount, Comment::getCreateTime));
    }

    private List<MediaExternalResource> loadActiveExternalResources() {
        return mediaExternalResourceMapper.selectList(new LambdaQueryWrapper<MediaExternalResource>()
                .eq(MediaExternalResource::getDeleted, 0)
                .isNotNull(MediaExternalResource::getMediaId)
                .in(MediaExternalResource::getSyncStatus, List.of("LINKED", "CREATED"))
                .orderByDesc(MediaExternalResource::getMatchConfidence, MediaExternalResource::getLastSyncedAt));
    }

    private List<MediaExternalResource> loadExternalResourcesByMediaIds(Set<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return List.of();
        }
        return mediaExternalResourceMapper.selectList(new LambdaQueryWrapper<MediaExternalResource>()
                .eq(MediaExternalResource::getDeleted, 0)
                .in(MediaExternalResource::getSyncStatus, List.of("LINKED", "CREATED"))
                .in(MediaExternalResource::getMediaId, mediaIds)
                .orderByDesc(MediaExternalResource::getMatchConfidence, MediaExternalResource::getLastSyncedAt));
    }

    private boolean checkPostgresReady() {
        try {
            Long value = ragMetadataRepository.chunkCount();
            return value >= 0;
        } catch (Exception e) {
            log.warn("PostgreSQL health check failed", e);
            return false;
        }
    }

    private String buildMediaProfileContent(Media media) {
        return """
                title: %s
                original_title: %s
                type: %s
                status: %s
                release_date: %s
                rating: %s
                summary:
                %s
                """.formatted(
                safe(media.getTitle()),
                safe(media.getOriginalTitle()),
                safe(media.getType()),
                safe(media.getStatus()),
                safe(media.getReleaseDate()),
                safe(media.getRating()),
                safe(media.getSummary())
        );
    }

    private String buildCommentContent(Media media, Comment comment) {
        return """
                media_title: %s
                media_rating: %s
                comment_rating: %s
                like_count: %s
                audience_voice:
                %s
                """.formatted(
                safe(media.getTitle()),
                safe(media.getRating()),
                safe(comment.getRating()),
                safe(comment.getLikeCount()),
                safe(comment.getContent())
        );
    }

    private String buildExternalMediaContent(MediaExternalResource resource) {
        String availabilitySummary = "synced from %s (%s) at %s".formatted(
                safe(resource.getProviderName()),
                safe(resource.getSourceKey()),
                safe(resource.getLastSyncedAt())
        );
        return """
                canonical_title: %s
                source_title: %s
                provider_name: %s
                source_key: %s
                release_year: %s
                type: %s
                rating: %s
                region: %s
                director: %s
                actors: %s
                availability_summary: %s
                description:
                %s
                """.formatted(
                safe(resource.getCleanTitle()),
                safe(resource.getRawTitle()),
                safe(resource.getProviderName()),
                safe(resource.getSourceKey()),
                safe(resource.getReleaseYear()),
                safe(resource.getType()),
                safe(resource.getRating()),
                safe(resource.getRegion()),
                safe(resource.getDirector()),
                safe(resource.getActors()),
                availabilitySummary,
                safe(resource.getDescription())
        );
    }

    private int estimateTokens(String content) {
        return content == null ? 0 : Math.max(1, content.length() / 2);
    }

    private String md5(String value) {
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void assertEnabled() {
        if (!ragProperties.isEnable()) {
            throw new BusinessException(400, "RAG is disabled");
        }
    }
}
