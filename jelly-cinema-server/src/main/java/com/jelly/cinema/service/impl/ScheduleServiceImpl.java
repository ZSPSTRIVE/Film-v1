package com.jelly.cinema.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jelly.cinema.mapper.ScheduleMapper;
import com.jelly.cinema.model.entity.Schedule;
import com.jelly.cinema.model.vo.ScheduleVo;
import com.jelly.cinema.service.ScheduleService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ScheduleServiceImpl extends ServiceImpl<ScheduleMapper, Schedule> implements ScheduleService {

    @Override
    public List<ScheduleVo> getCinemaSchedules(Long cinemaId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Schedule::getCinemaId, cinemaId)
               .eq(Schedule::getStatus, 1) // 正常排期状态
               .ge(Schedule::getShowTime, startOfDay)
               .le(Schedule::getShowTime, endOfDay)
               .orderByAsc(Schedule::getShowTime);
               
        List<Schedule> list = this.list(wrapper);
        
        return list.stream().map(schedule -> {
            ScheduleVo vo = new ScheduleVo();
            BeanUtil.copyProperties(schedule, vo);
            // 此处后续可根据 hallId 查询关联的影厅名以补充信息
            return vo;
        }).collect(Collectors.toList());
    }
}
