package com.jelly.cinema.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.entity.Comment;
import com.jelly.cinema.mapper.ActorMapper;
import com.jelly.cinema.mapper.CommentMapper;
import com.jelly.cinema.mapper.MediaCastMapper;
import com.jelly.cinema.mapper.MediaExternalResourceMapper;
import com.jelly.cinema.mapper.MediaMapper;
import com.jelly.cinema.mapper.MediaPlaySourceMapper;
import com.jelly.cinema.model.entity.Actor;
import com.jelly.cinema.model.entity.Media;
import com.jelly.cinema.model.entity.MediaCast;
import com.jelly.cinema.model.entity.MediaExternalResource;
import com.jelly.cinema.model.entity.MediaPlaySource;
import com.jelly.cinema.service.ai.rag.RagQaContext;
import com.jelly.cinema.service.ai.rag.RagQaContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaAiToolService {

    private final MediaMapper mediaMapper;
    private final CommentMapper commentMapper;
    private final MediaCastMapper mediaCastMapper;
    private final ActorMapper actorMapper;
    private final MediaPlaySourceMapper mediaPlaySourceMapper;
    private final MediaExternalResourceMapper mediaExternalResourceMapper;
    private final RagQaContextService ragQaContextService;

    public Map<String, Object> loadMediaProfile(MediaToolRequest request) {
        Media media = mediaMapper.selectById(request.getMediaId());
        if (media == null) {
            return Map.of("exists", false, "message", "media not found");
        }

        List<MediaCast> casts = mediaCastMapper.selectList(new LambdaQueryWrapper<MediaCast>()
                .eq(MediaCast::getMediaId, request.getMediaId()));
        List<Actor> actors = casts.isEmpty() ? List.of()
                : actorMapper.selectByIds(casts.stream().map(MediaCast::getActorId).collect(Collectors.toList()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", true);
        result.put("title", media.getTitle());
        result.put("originalTitle", media.getOriginalTitle());
        result.put("type", media.getType());
        result.put("status", media.getStatus());
        result.put("releaseDate", media.getReleaseDate());
        result.put("rating", media.getRating());
        result.put("summary", media.getSummary());
        result.put("actors", actors.stream()
                .map(actor -> {
                    Map<String, Object> actorData = new LinkedHashMap<>();
                    actorData.put("id", actor.getId());
                    actorData.put("name", actor.getName());
                    actorData.put("bio", actor.getBio() == null ? "" : actor.getBio());
                    return actorData;
                })
                .collect(Collectors.toList()));
        return result;
    }

    public Map<String, Object> loadAudienceVoices(MediaToolRequest request) {
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getMediaId, request.getMediaId())
                .eq(Comment::getAuditStatus, 1)
                .isNotNull(Comment::getContent)
                .orderByDesc(Comment::getLikeCount, Comment::getCreateTime)
                .last("limit 8"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", comments.size());
        result.put("comments", comments.stream()
                .map(comment -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("content", comment.getContent());
                    item.put("rating", comment.getRating());
                    item.put("likeCount", comment.getLikeCount());
                    item.put("auditStatus", comment.getAuditStatus());
                    return item;
                })
                .collect(Collectors.toList()));
        return result;
    }

    public Map<String, Object> loadMediaRagEvidence(MediaQuestionToolRequest request) {
        Media media = mediaMapper.selectById(request.getMediaId());
        if (media == null) {
            return Map.of("count", 0, "mode", "tool_only", "citations", List.of());
        }

        RagQaContext context = ragQaContextService.buildMediaQaContext(request.getMediaId(), media.getTitle(), request.getQuestion());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", context.getCitations().size());
        result.put("mode", context.getRetrievalMode());
        result.put("citations", context.getCitations().stream()
                .map(citation -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("title", citation.getTitle());
                    item.put("snippet", citation.getSnippet());
                    item.put("source", citation.getSource());
                    item.put("knowledgeBaseCode", citation.getKnowledgeBaseCode());
                    item.put("score", citation.getScore());
                    return item;
                })
                .collect(Collectors.toList()));
        return result;
    }

    public Map<String, Object> loadSourceProviderFacts(MediaToolRequest request) {
        List<MediaExternalResource> resources = mediaExternalResourceMapper.selectList(new LambdaQueryWrapper<MediaExternalResource>()
                .eq(MediaExternalResource::getMediaId, request.getMediaId())
                .eq(MediaExternalResource::getDeleted, 0)
                .in(MediaExternalResource::getSyncStatus, List.of("LINKED", "CREATED"))
                .orderByDesc(MediaExternalResource::getMatchConfidence, MediaExternalResource::getLastSyncedAt)
                .last("limit 20"));

        List<MediaPlaySource> sources = mediaPlaySourceMapper.selectList(new LambdaQueryWrapper<MediaPlaySource>()
                .eq(MediaPlaySource::getMediaId, request.getMediaId())
                .eq(MediaPlaySource::getDeleted, 0)
                .orderByAsc(MediaPlaySource::getSortOrder, MediaPlaySource::getId)
                .last("limit 20"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("externalResourceCount", resources.size());
        result.put("playSourceCount", sources.size());
        result.put("externalResources", resources.stream()
                .map(resource -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("providerName", resource.getProviderName());
                    item.put("sourceKey", resource.getSourceKey());
                    item.put("rawTitle", resource.getRawTitle());
                    item.put("cleanTitle", resource.getCleanTitle());
                    item.put("releaseYear", resource.getReleaseYear());
                    item.put("region", resource.getRegion());
                    item.put("director", resource.getDirector());
                    item.put("actors", resource.getActors());
                    item.put("rating", resource.getRating());
                    item.put("syncStatus", resource.getSyncStatus());
                    item.put("lastSyncedAt", resource.getLastSyncedAt());
                    return item;
                })
                .collect(Collectors.toList()));
        result.put("playSources", sources.stream()
                .map(source -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("sourceType", source.getSourceType());
                    item.put("providerName", source.getProviderName());
                    item.put("title", source.getTitle());
                    item.put("region", source.getRegion());
                    item.put("quality", source.getQuality());
                    item.put("isFree", source.getIsFree());
                    item.put("url", source.getUrl());
                    item.put("updatedAt", source.getUpdateTime() == null ? source.getCreateTime() : source.getUpdateTime());
                    return item;
                })
                .collect(Collectors.toList()));
        return result;
    }

    public static class MediaToolRequest {
        private Long mediaId;

        public Long getMediaId() {
            return mediaId;
        }

        public void setMediaId(Long mediaId) {
            this.mediaId = mediaId;
        }
    }

    public static class MediaQuestionToolRequest extends MediaToolRequest {
        private String question;

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }
    }
}
