package com.jelly.cinema.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.model.entity.Media;
import org.apache.ibatis.annotations.Mapper;

/**
 * 影视内容 Mapper 接口
 */
@Mapper
public interface MediaMapper extends BaseMapper<Media> {
}
