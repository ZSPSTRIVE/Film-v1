package com.jelly.cinema.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jelly.cinema.common.exception.BusinessException;
import com.jelly.cinema.entity.Comment;
import com.jelly.cinema.mapper.ActorMapper;
import com.jelly.cinema.mapper.CommentMapper;
import com.jelly.cinema.mapper.MediaCastMapper;
import com.jelly.cinema.mapper.MediaMapper;
import com.jelly.cinema.model.dto.MediaSearchDTO;
import com.jelly.cinema.model.entity.Actor;
import com.jelly.cinema.model.entity.Media;
import com.jelly.cinema.model.entity.MediaCast;
import com.jelly.cinema.model.vo.AiSearchVO;
import com.jelly.cinema.model.vo.MediaDetailVO;
import com.jelly.cinema.service.CinemaAiService;
import com.jelly.cinema.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaServiceImpl extends ServiceImpl<MediaMapper, Media> implements MediaService {

    private final MediaCastMapper mediaCastMapper;
    private final ActorMapper actorMapper;
    private final CommentMapper commentMapper;
    private final CinemaAiService cinemaAiService;

    @Override
    public Page<Media> searchMedia(MediaSearchDTO dto) {
        Page<Media> page = new Page<>(dto.getPage(), dto.getPageSize());
        LambdaQueryWrapper<Media> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(dto.getKeyword())) {
            wrapper.and(q -> q.like(Media::getTitle, dto.getKeyword())
                    .or()
                    .like(Media::getOriginalTitle, dto.getKeyword())
                    .or()
                    .like(Media::getSummary, dto.getKeyword()));
        }
        if (dto.getType() != null && dto.getType() != 0) {
            wrapper.eq(Media::getType, dto.getType());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(Media::getStatus, dto.getStatus());
        }

        wrapper.eq(Media::getDeleted, 0).orderByDesc(Media::getRating, Media::getReleaseDate);
        return this.page(page, wrapper);
    }

    @Override
    public MediaDetailVO getMediaDetail(Long id) {
        Media media = this.getById(id);
        if (media == null || media.getDeleted() == 1) {
            throw new BusinessException(404, "影视内容不存在");
        }

        MediaDetailVO vo = new MediaDetailVO();
        org.springframework.beans.BeanUtils.copyProperties(media, vo);
        vo.setCommentCount(Math.toIntExact(commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>().eq(Comment::getMediaId, id)
        )));

        List<MediaCast> casts = mediaCastMapper.selectList(
                new LambdaQueryWrapper<MediaCast>().eq(MediaCast::getMediaId, id)
        );

        if (!casts.isEmpty()) {
            List<Long> actorIds = casts.stream().map(MediaCast::getActorId).collect(Collectors.toList());
            List<Actor> actors = actorMapper.selectByIds(actorIds);

            List<MediaDetailVO.ActorVO> actorVOs = casts.stream().map(cast -> {
                MediaDetailVO.ActorVO actorVO = new MediaDetailVO.ActorVO();
                actorVO.setRoleType(cast.getRoleType());
                actorVO.setCharacterName(cast.getCharacterName());
                actors.stream()
                        .filter(actor -> actor.getId().equals(cast.getActorId()))
                        .findFirst()
                        .ifPresent(actor -> {
                            actorVO.setId(actor.getId());
                            actorVO.setName(actor.getName());
                            actorVO.setAvatarUrl(actor.getAvatarUrl());
                        });
                return actorVO;
            }).collect(Collectors.toList());
            vo.setActors(actorVOs);
        } else {
            vo.setActors(Collections.emptyList());
        }

        return vo;
    }

    @Override
    public AiSearchVO naturalLanguageSearch(String query) {
        return cinemaAiService.naturalLanguageSearch(query, 1, 12);
    }

    @Override
    public String generateSummary(Long mediaId, String originalSummary) {
        return cinemaAiService.generateMediaSummary(mediaId, originalSummary);
    }
}
