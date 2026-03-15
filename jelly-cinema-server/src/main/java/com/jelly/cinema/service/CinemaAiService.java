package com.jelly.cinema.service;

import com.jelly.cinema.model.dto.ai.AiBannerCopyRequest;
import com.jelly.cinema.model.dto.ai.AiCommentAuditRequest;
import com.jelly.cinema.model.dto.ai.AiMediaQuestionRequest;
import com.jelly.cinema.model.vo.AiBannerCopyVO;
import com.jelly.cinema.model.vo.AiCommentAuditVO;
import com.jelly.cinema.model.vo.AiMediaQaVO;
import com.jelly.cinema.model.vo.AiSearchVO;

public interface CinemaAiService {

    String chat(String message);

    AiSearchVO naturalLanguageSearch(String query, Integer page, Integer pageSize);

    String generateMediaSummary(Long mediaId, String originalSummary);

    AiMediaQaVO answerMediaQuestion(AiMediaQuestionRequest request);

    AiBannerCopyVO generateBannerCopy(AiBannerCopyRequest request);

    AiCommentAuditVO auditComment(AiCommentAuditRequest request);
}
