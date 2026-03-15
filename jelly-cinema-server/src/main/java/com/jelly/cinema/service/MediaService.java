package com.jelly.cinema.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jelly.cinema.model.dto.MediaSearchDTO;
import com.jelly.cinema.model.entity.Media;
import com.jelly.cinema.model.vo.AiSearchVO;
import com.jelly.cinema.model.vo.MediaDetailVO;

public interface MediaService extends IService<Media> {

    Page<Media> searchMedia(MediaSearchDTO dto);

    MediaDetailVO getMediaDetail(Long id);

    AiSearchVO naturalLanguageSearch(String query);

    String generateSummary(Long mediaId, String originalSummary);
}
