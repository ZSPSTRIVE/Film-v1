package com.jelly.cinema.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.entity.Comment;
import com.jelly.cinema.mapper.CommentMapper;
import com.jelly.cinema.mapper.MediaMapper;
import com.jelly.cinema.model.entity.Media;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaAiToolService {

    private final MediaMapper mediaMapper;
    private final CommentMapper commentMapper;

    public Map<String, Object> loadMediaProfile(MediaToolRequest request) {
        Media media = mediaMapper.selectById(request.getMediaId());
        if (media == null) {
            return Map.of("exists", false, "message", "影片不存在");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", true);
        result.put("title", media.getTitle());
        result.put("originalTitle", media.getOriginalTitle());
        result.put("type", media.getType());
        result.put("status", media.getStatus());
        result.put("releaseDate", media.getReleaseDate());
        result.put("rating", media.getRating());
        result.put("summary", media.getSummary());
        return result;
    }

    public Map<String, Object> loadAudienceVoices(MediaToolRequest request) {
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getMediaId, request.getMediaId())
                .orderByDesc(Comment::getLikeCount, Comment::getCreateTime)
                .last("limit 5"));

        return Map.of(
                "count", comments.size(),
                "comments", comments.stream()
                        .map(comment -> Map.of(
                                "content", comment.getContent(),
                                "rating", comment.getRating(),
                                "likeCount", comment.getLikeCount(),
                                "auditStatus", comment.getAuditStatus()
                        ))
                        .collect(Collectors.toList())
        );
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
}
