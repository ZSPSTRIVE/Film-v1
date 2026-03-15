package com.jelly.cinema.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.mapper.MediaMapper;
import com.jelly.cinema.model.entity.Media;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MysqlMediaRetrievalService implements MediaRetrievalService {

    private final MediaMapper mediaMapper;

    @Override
    public List<MediaSearchHit> retrieve(AiSearchPlan plan, int size) {
        LambdaQueryWrapper<Media> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Media::getDeleted, 0);

        if (StringUtils.hasText(plan.getNormalizedQuery())) {
            wrapper.and(q -> q.like(Media::getTitle, plan.getNormalizedQuery())
                    .or()
                    .like(Media::getOriginalTitle, plan.getNormalizedQuery())
                    .or()
                    .like(Media::getSummary, plan.getNormalizedQuery()));
        }

        if (plan.getType() != null && plan.getType() > 0) {
            wrapper.eq(Media::getType, plan.getType());
        }

        if (plan.getStatus() != null) {
            wrapper.eq(Media::getStatus, plan.getStatus());
        }

        if ("releaseDate".equalsIgnoreCase(plan.getSortBy())) {
            wrapper.orderByDesc(Media::getReleaseDate, Media::getRating);
        } else {
            wrapper.orderByDesc(Media::getRating, Media::getReleaseDate);
        }

        wrapper.last("limit " + Math.max(1, size));
        return mediaMapper.selectList(wrapper).stream()
                .map(media -> new MediaSearchHit(media, null, buildHighlight(media, plan.getNormalizedQuery()), "mysql"))
                .collect(Collectors.toList());
    }

    private String buildHighlight(Media media, String normalizedQuery) {
        if (!StringUtils.hasText(media.getSummary())) {
            return media.getTitle();
        }
        if (!StringUtils.hasText(normalizedQuery)) {
            return media.getSummary().substring(0, Math.min(media.getSummary().length(), 80));
        }

        int index = media.getSummary().indexOf(normalizedQuery);
        if (index < 0) {
            return media.getSummary().substring(0, Math.min(media.getSummary().length(), 80));
        }

        int start = Math.max(0, index - 20);
        int end = Math.min(media.getSummary().length(), index + normalizedQuery.length() + 40);
        return media.getSummary().substring(start, end);
    }
}
