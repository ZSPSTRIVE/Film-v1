package com.jelly.cinema.controller.app;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.common.R;
import com.jelly.cinema.model.vo.MediaVo;
import com.jelly.cinema.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "App-影视内容模块")
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @Operation(summary = "多模态影视分页列表")
    @GetMapping("/list")
    public R<Page<MediaVo>> getList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String keyword) {
        
        Page<MediaVo> page = mediaService.getMediaPage(pageNum, pageSize, type, keyword);
        return R.ok(page);
    }

    @Operation(summary = "获取影视内容详情")
    @GetMapping("/{id}")
    public R<MediaVo> getDetail(@PathVariable Long id) {
        MediaVo vo = mediaService.getMediaDetail(id);
        return R.ok(vo);
    }
}
