package com.jelly.cinema.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jelly.cinema.model.entity.Schedule;
import com.jelly.cinema.model.vo.ScheduleVo;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleService extends IService<Schedule> {

    /**
     * 获取某影院某日的排期列表
     */
    List<ScheduleVo> getCinemaSchedules(Long cinemaId, LocalDate date);
}
