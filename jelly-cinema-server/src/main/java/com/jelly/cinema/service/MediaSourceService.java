package com.jelly.cinema.service;

import com.jelly.cinema.model.vo.MediaPlaySourceVO;

public interface MediaSourceService {

    MediaPlaySourceVO getPlaySources(Long mediaId);

    int syncTvboxSources(Long mediaId);
}
