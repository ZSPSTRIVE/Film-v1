package com.jelly.cinema.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.common.result.R;
import com.jelly.cinema.model.dto.MediaSearchDTO;
import com.jelly.cinema.model.dto.ai.AiMediaQuestionRequest;
import com.jelly.cinema.model.entity.Media;
import com.jelly.cinema.model.vo.AiMediaQaVO;
import com.jelly.cinema.model.vo.AiSearchVO;
import com.jelly.cinema.model.vo.MediaDetailVO;
import com.jelly.cinema.service.CinemaAiService;
import com.jelly.cinema.service.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;
    private final CinemaAiService cinemaAiService;

    @GetMapping("/search")
    public R<Page<Media>> searchMedia(MediaSearchDTO dto) {
        return R.ok(mediaService.searchMedia(dto));
    }

    @GetMapping("/{id}")
    public R<MediaDetailVO> getMediaDetail(@PathVariable Long id) {
        return R.ok(mediaService.getMediaDetail(id));
    }

    @GetMapping("/ai-search")
    public R<AiSearchVO> naturalLanguageSearch(@RequestParam("query") String query,
                                               @RequestParam(value = "page", required = false) Integer page,
                                               @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return R.ok(cinemaAiService.naturalLanguageSearch(query, page, pageSize));
    }

    @PostMapping("/ai-summary")
    public R<String> generateAiSummary(@RequestParam("mediaId") Long mediaId,
                                       @RequestParam("originalSummary") String originalSummary) {
        return R.ok(mediaService.generateSummary(mediaId, originalSummary));
    }

    @PostMapping("/{id}/ai-question")
    public R<AiMediaQaVO> answerMediaQuestion(@PathVariable Long id,
                                              @Valid @RequestBody AiMediaQuestionRequest request) {
        request.setMediaId(id);
        return R.ok(cinemaAiService.answerMediaQuestion(request));
    }
}
