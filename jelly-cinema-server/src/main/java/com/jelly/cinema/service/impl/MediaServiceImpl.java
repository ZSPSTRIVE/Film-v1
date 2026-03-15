package com.jelly.cinema.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jelly.cinema.mapper.MediaMapper;
import com.jelly.cinema.model.entity.Media;
import com.jelly.cinema.model.vo.MediaVo;
import com.jelly.cinema.service.MediaService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MediaServiceImpl extends ServiceImpl<MediaMapper, Media> implements MediaService {

    @Override
    public Page<MediaVo> getMediaPage(int pageNum, int pageSize, Integer type, String keyword) {
        Page<Media> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Media> wrapper = new LambdaQueryWrapper<>();
        
        if (type != null) {
            wrapper.eq(Media::getType, type);
        }
        // 当状态值为非下架(3)展示
        wrapper.ne(Media::getStatus, 3);

        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(i -> i.like(Media::getTitle, keyword)
                             .or()
                             .like(Media::getOriginalTitle, keyword));
        }
        
        // 默认按发布日期降序
        wrapper.orderByDesc(Media::getReleaseDate);
        
        Page<Media> mediaPage = this.page(page, wrapper);
        
        List<MediaVo> voList = mediaPage.getRecords().stream().map(media -> {
            MediaVo vo = new MediaVo();
            BeanUtil.copyProperties(media, vo);
            return vo;
        }).collect(Collectors.toList());
        
        Page<MediaVo> result = new Page<>(mediaPage.getCurrent(), mediaPage.getSize(), mediaPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    @Override
    public MediaVo getMediaDetail(Long id) {
        Media media = this.getById(id);
        if (media == null || (media.getStatus() != null && media.getStatus() == 3)) {
            throw new RuntimeException("影视内容不存在或已下架");
        }
        MediaVo vo = new MediaVo();
        BeanUtil.copyProperties(media, vo);
        // FIXME: 后续在这里整合通过 ActorService 查询挂载对应的演职员列表和剧集分集信息，作为复合领域对象返回
        return vo;
    }
}
