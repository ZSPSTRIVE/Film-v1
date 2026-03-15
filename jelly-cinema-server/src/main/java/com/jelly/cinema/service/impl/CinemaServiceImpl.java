package com.jelly.cinema.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jelly.cinema.mapper.CinemaMapper;
import com.jelly.cinema.model.entity.Cinema;
import com.jelly.cinema.model.vo.CinemaVo;
import com.jelly.cinema.service.CinemaService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CinemaServiceImpl extends ServiceImpl<CinemaMapper, Cinema> implements CinemaService {

    @Override
    public Page<CinemaVo> getNearbyCinemas(int pageNum, int pageSize, String city, Double lng, Double lat) {
        Page<Cinema> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Cinema> wrapper = new LambdaQueryWrapper<>();
        
        if (StrUtil.isNotBlank(city)) {
            wrapper.eq(Cinema::getCity, city);
        }
        
        // 简化查询处理，实际生产中可使用 Redis Geo 进行严格范围检索并排序
        Page<Cinema> cinemaPage = this.page(page, wrapper);
        
        List<CinemaVo> voList = cinemaPage.getRecords().stream().map(cinema -> {
            CinemaVo vo = new CinemaVo();
            BeanUtil.copyProperties(cinema, vo);
            // 若上送了经纬度，这里可以补充模拟一个直线距离的计算逻辑
            return vo;
        }).collect(Collectors.toList());
        
        Page<CinemaVo> result = new Page<>(cinemaPage.getCurrent(), cinemaPage.getSize(), cinemaPage.getTotal());
        result.setRecords(voList);
        return result;
    }
}
