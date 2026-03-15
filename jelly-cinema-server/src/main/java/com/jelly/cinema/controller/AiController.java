package com.jelly.cinema.controller;

import com.jelly.cinema.common.result.R;
import com.jelly.cinema.model.dto.ai.AiBannerCopyRequest;
import com.jelly.cinema.model.dto.ai.AiCommentAuditRequest;
import com.jelly.cinema.model.dto.ai.AiMediaQuestionRequest;
import com.jelly.cinema.model.vo.AiBannerCopyVO;
import com.jelly.cinema.model.vo.AiCommentAuditVO;
import com.jelly.cinema.model.vo.AiMediaQaVO;
import com.jelly.cinema.service.CinemaAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final CinemaAiService cinemaAiService;

    @GetMapping("/chat")
    public R<String> chat(@RequestParam String message) {
        return R.ok(cinemaAiService.chat(message));
    }

    @PostMapping("/media-qa")
    public R<AiMediaQaVO> mediaQa(@Valid @RequestBody AiMediaQuestionRequest request) {
        return R.ok(cinemaAiService.answerMediaQuestion(request));
    }

    @PostMapping("/banner-copy")
    public R<AiBannerCopyVO> bannerCopy(@Valid @RequestBody AiBannerCopyRequest request) {
        return R.ok(cinemaAiService.generateBannerCopy(request));
    }

    @PostMapping("/comment-audit")
    public R<AiCommentAuditVO> commentAudit(@Valid @RequestBody AiCommentAuditRequest request) {
        return R.ok(cinemaAiService.auditComment(request));
    }
}
