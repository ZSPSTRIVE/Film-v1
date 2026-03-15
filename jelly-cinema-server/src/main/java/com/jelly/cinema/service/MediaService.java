package com.jelly.cinema.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jelly.cinema.model.entity.Media;
import com.jelly.cinema.model.vo.MediaVo;

public interface MediaService extends IService<Media> {

    /**
     * 分页查询影视列表
     * @param pageNum 当前页码
     * @param pageSize 每页记录数
     * @param type 影视类型 (1电影 2电视剧 3动漫)
     * @param keyword 搜索关键字 (模糊匹配标题)
     * @return 分页结果
     */
    Page<MediaVo> getMediaPage(int pageNum, int pageSize, Integer type, String keyword);

    /**
     * 获取影视详情
     * @param id 影视ID
     * @return 影视详情视图对象
     */
    MediaVo getMediaDetail(Long id);
}
