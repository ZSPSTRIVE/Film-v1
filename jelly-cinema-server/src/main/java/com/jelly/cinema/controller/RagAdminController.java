package com.jelly.cinema.controller;

import com.jelly.cinema.common.result.R;
import com.jelly.cinema.model.vo.RagAdminStatusVO;
import com.jelly.cinema.model.vo.RagRebuildResultVO;
import com.jelly.cinema.service.ai.rag.RagIndexingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagAdminController {

    private final RagIndexingService ragIndexingService;

    @GetMapping("/status")
    public R<RagAdminStatusVO> status() {
        return R.ok(ragIndexingService.status());
    }

    @PostMapping("/rebuild")
    public R<RagRebuildResultVO> rebuildAll() {
        return R.ok(ragIndexingService.rebuildAll());
    }

    @PostMapping("/rebuild/media/{mediaId}")
    public R<RagRebuildResultVO> rebuildMedia(@PathVariable Long mediaId) {
        return R.ok(ragIndexingService.rebuildMedia(mediaId));
    }
}
