package com.jelly.cinema.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jelly.cinema.model.entity.Cinema;
import com.jelly.cinema.model.vo.CinemaVo;

public interface CinemaService extends IService<Cinema> {

    /**
     * 查询附近影院(简单模式)
     */
    Page<CinemaVo> getNearbyCinemas(int pageNum, int pageSize, String city, Double lng, Double lat);
}
