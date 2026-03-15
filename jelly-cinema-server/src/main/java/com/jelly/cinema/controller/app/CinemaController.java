package com.jelly.cinema.controller.app;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.common.R;
import com.jelly.cinema.model.vo.CinemaVo;
import com.jelly.cinema.model.vo.ScheduleVo;
import com.jelly.cinema.service.CinemaService;
import com.jelly.cinema.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "App-影院与排片模块")
@RestController
@RequestMapping("/api/v1/cinema")
@RequiredArgsConstructor
public class CinemaController {

    private final CinemaService cinemaService;
    private final ScheduleService scheduleService;

    @Operation(summary = "检索附近影院")
    @GetMapping("/nearby")
    public R<Page<CinemaVo>> getNearbyCinemas(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double lat) {
        
        Page<CinemaVo> page = cinemaService.getNearbyCinemas(pageNum, pageSize, city, lng, lat);
        return R.ok(page);
    }

    @Operation(summary = "查询某影院单日的全部排片轴")
    @GetMapping("/{cinemaId}/schedules")
    public R<List<ScheduleVo>> getCinemaSchedules(
            @PathVariable Long cinemaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        List<ScheduleVo> list = scheduleService.getCinemaSchedules(cinemaId, date);
        return R.ok(list);
    }
}
