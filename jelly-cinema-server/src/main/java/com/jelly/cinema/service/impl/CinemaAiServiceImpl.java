package com.jelly.cinema.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.common.config.property.AiProperties;
import com.jelly.cinema.common.exception.BusinessException;
import com.jelly.cinema.mapper.MediaMapper;
import com.jelly.cinema.mapper.SearchKeywordLogMapper;
import com.jelly.cinema.model.dto.ai.AiBannerCopyRequest;
import com.jelly.cinema.model.dto.ai.AiCommentAuditRequest;
import com.jelly.cinema.model.dto.ai.AiMediaQuestionRequest;
import com.jelly.cinema.model.entity.Media;
import com.jelly.cinema.model.entity.SearchKeywordLog;
import com.jelly.cinema.model.vo.AiBannerCopyVO;
import com.jelly.cinema.model.vo.AiCitationVO;
import com.jelly.cinema.model.vo.AiCommentAuditVO;
import com.jelly.cinema.model.vo.AiMediaQaVO;
import com.jelly.cinema.model.vo.AiSearchVO;
import com.jelly.cinema.service.AiFailoverService;
import com.jelly.cinema.service.AiPromptTemplateService;
import com.jelly.cinema.service.CinemaAiService;
import com.jelly.cinema.service.ai.AiAccessGuardService;
import com.jelly.cinema.service.ai.AiAuditRecordService;
import com.jelly.cinema.service.ai.AiCacheService;
import com.jelly.cinema.service.ai.AiQueryMode;
import com.jelly.cinema.service.ai.AiPromptScene;
import com.jelly.cinema.service.ai.AiSearchQueryHelper;
import com.jelly.cinema.service.ai.AiSearchPlan;
import com.jelly.cinema.service.ai.HybridMediaRetrievalService;
import com.jelly.cinema.service.ai.MediaAiToolService;
import com.jelly.cinema.service.ai.MediaSearchHit;
import com.jelly.cinema.service.ai.rag.RagQaContext;
import com.jelly.cinema.service.ai.rag.RagQaContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CinemaAiServiceImpl implements CinemaAiService {

    private final AiFailoverService aiFailoverService;
    private final AiPromptTemplateService aiPromptTemplateService;
    private final AiAccessGuardService aiAccessGuardService;
    private final AiCacheService aiCacheService;
    private final AiAuditRecordService aiAuditRecordService;
    private final HybridMediaRetrievalService hybridMediaRetrievalService;
    private final MediaMapper mediaMapper;
    private final SearchKeywordLogMapper searchKeywordLogMapper;
    private final MediaAiToolService mediaAiToolService;
    private final RagQaContextService ragQaContextService;
    private final TvboxSyncCacheService tvboxSyncCacheService;
    private final AiProperties aiProperties;

    private final ChatMemory chatMemory = new InMemoryChatMemory();

    @Override
    public String chat(String message) {
        aiAccessGuardService.assertAllowed(AiPromptScene.GENERAL_CHAT.getCode());
        return aiFailoverService.execute(AiPromptScene.GENERAL_CHAT.getCode(), client -> client.prompt()
                .system(aiPromptTemplateService.getTemplate(AiPromptScene.GENERAL_CHAT))
                .user(message)
                .options(aiPromptTemplateService.buildOptions(AiPromptScene.GENERAL_CHAT))
                .call()
                .content());
    }

    @Override
    public AiSearchVO naturalLanguageSearch(String query, Integer page, Integer pageSize) {
        if (!StringUtils.hasText(query)) {
            throw new BusinessException(400, "搜索词不能为空");
        }

        aiAccessGuardService.assertAllowed(AiPromptScene.MEDIA_SEARCH_PLAN.getCode());
        int currentPage = page == null || page < 1 ? 1 : page;
        int currentPageSize = pageSize == null || pageSize < 1 ? 12 : Math.min(pageSize, 24);
        logSearchKeyword(query);

        String cacheKey = aiCacheService.buildKey("search", "v4|" + query + "|" + currentPage + "|" + currentPageSize);
        TvboxSyncResult syncResult = tvboxSyncCacheService.syncIfNotCached(query, Math.max(currentPageSize * 3, 24));
        if (syncResult.getSyncedExternalResourceCount() > 0) {
            aiCacheService.evict(cacheKey);
            log.info("AI search pre-ingest completed. query={}, externalCount={}, affectedMediaCount={}",
                    query,
                    syncResult.getSyncedExternalResourceCount(),
                    syncResult.getAffectedMediaIds().size());
        } else {
            AiSearchVO cached = aiCacheService.get(cacheKey, AiSearchVO.class);
            if (cached != null) {
                return cached;
            }
        }

        AiSearchPlan searchPlan = buildSearchPlan(query, currentPage, currentPageSize);
        List<MediaSearchHit> hits = hybridMediaRetrievalService.retrieve(searchPlan, currentPageSize);
        if (hits.isEmpty() && StringUtils.hasText(query) && !query.equals(searchPlan.getNormalizedQuery())) {
            searchPlan.setNormalizedQuery(query.trim());
            searchPlan.setSemanticQuery(query.trim());
            hits = hybridMediaRetrievalService.retrieve(searchPlan, currentPageSize);
        }

        if (hits.isEmpty()) {
            hits = retryRetrieveWithRefinedKeywords(query, searchPlan, currentPageSize);
        }

        AiQueryMode queryMode = resolveQueryMode(searchPlan, query);
        if (queryMode == AiQueryMode.TITLE) {
            List<MediaSearchHit> preciseHits = retrievePreciseTitleHits(searchPlan.getNormalizedQuery(), searchPlan, currentPageSize);
            hits = mergePreferPreciseHits(preciseHits, hits, currentPageSize);
            hits = applyPrecisionGuard(searchPlan.getNormalizedQuery(), hits);
        }
        if (hits.isEmpty()) {
            hits = retrieveIntentFallbackHits(query, searchPlan, currentPageSize);
        }

        AiSearchVO vo = new AiSearchVO();
        vo.setMediaList(hits.stream().map(MediaSearchHit::getMedia).collect(Collectors.toList()));
        vo.setMatchedCount(vo.getMediaList().size());
        vo.setNormalizedQuery(searchPlan.getNormalizedQuery());
        vo.setIntentSummary(searchPlan.getIntentSummary());
        vo.setRetrievalMode(hits.isEmpty() ? "none" : hits.get(0).getSource());
        vo.setCitations(buildSearchCitations(hits));
        vo.setAnswer(buildSearchAnswer(query, searchPlan, hits));

        aiCacheService.put(cacheKey, vo, Duration.ofSeconds(aiProperties.getCache().getSearchTtlSeconds()));
        aiAuditRecordService.record(AiPromptScene.MEDIA_SEARCH_ANSWER.getCode(), query, vo.getAnswer());
        return vo;
    }

    private List<MediaSearchHit> retrievePreciseTitleHits(String normalizedQuery, AiSearchPlan plan, int size) {
        if (!StringUtils.hasText(normalizedQuery)) {
            return List.of();
        }
        LambdaQueryWrapper<Media> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Media::getDeleted, 0);
        wrapper.and(q -> q.like(Media::getTitle, normalizedQuery)
                .or()
                .like(Media::getOriginalTitle, normalizedQuery));
        if (plan.getType() != null && plan.getType() > 0) {
            wrapper.eq(Media::getType, plan.getType());
        }
        if (plan.getStatus() != null) {
            wrapper.eq(Media::getStatus, plan.getStatus());
        }
        wrapper.orderByDesc(Media::getRating, Media::getReleaseDate);
        wrapper.last("limit " + Math.max(1, size));

        return mediaMapper.selectList(wrapper).stream()
                .map(media -> {
                    MediaSearchHit hit = new MediaSearchHit(media, 1.2d, buildPreciseHighlight(media, normalizedQuery), "mysql_precise");
                    hit.setLexicalScore(1.0d);
                    hit.setRerankScore(1.2d);
                    return hit;
                })
                .toList();
    }

    private List<MediaSearchHit> mergePreferPreciseHits(List<MediaSearchHit> preciseHits, List<MediaSearchHit> originalHits, int size) {
        if ((preciseHits == null || preciseHits.isEmpty()) && (originalHits == null || originalHits.isEmpty())) {
            return List.of();
        }
        Map<Long, MediaSearchHit> merged = new LinkedHashMap<>();
        if (preciseHits != null) {
            for (MediaSearchHit hit : preciseHits) {
                if (hit.getMedia() == null || hit.getMedia().getId() == null) {
                    continue;
                }
                merged.put(hit.getMedia().getId(), hit);
            }
        }
        if (originalHits != null) {
            for (MediaSearchHit hit : originalHits) {
                if (hit.getMedia() == null || hit.getMedia().getId() == null) {
                    continue;
                }
                merged.putIfAbsent(hit.getMedia().getId(), hit);
            }
        }
        List<MediaSearchHit> result = new ArrayList<>(merged.values());
        if (result.size() > size) {
            return result.subList(0, size);
        }
        return result;
    }

    private List<MediaSearchHit> applyPrecisionGuard(String normalizedQuery, List<MediaSearchHit> hits) {
        if (!StringUtils.hasText(normalizedQuery) || hits == null || hits.isEmpty()) {
            return hits == null ? List.of() : hits;
        }

        String query = normalizedQuery.trim().toLowerCase();
        List<MediaSearchHit> strongHits = hits.stream()
                .filter(hit -> isStrongMatch(query, hit))
                .collect(Collectors.toList());
        if (!strongHits.isEmpty()) {
            return strongHits;
        }

        List<MediaSearchHit> mediumHits = hits.stream()
                .filter(h -> {
                    if (h == null || h.getMedia() == null) {
                        return false;
                    }
                    Double vec = h.getVectorScore();
                    java.math.BigDecimal ratBD = h.getMedia().getRating();
                    boolean qualityOk = (vec != null && vec >= 0.3)
                            || (ratBD != null && ratBD.doubleValue() >= 6.0);
                    return qualityOk && matchesIntentTerms(query, h);
                })
                .collect(Collectors.toList());

        if (!mediumHits.isEmpty()) {
            return mediumHits;
        }

        return List.of();
    }

    private boolean isStrongMatch(String query, MediaSearchHit hit) {
        if (hit == null || hit.getMedia() == null) {
            return false;
        }
        Media media = hit.getMedia();
        String title = media.getTitle() == null ? "" : media.getTitle().toLowerCase();
        String originalTitle = media.getOriginalTitle() == null ? "" : media.getOriginalTitle().toLowerCase();
        String summary = media.getSummary() == null ? "" : media.getSummary().toLowerCase();

        if (title.contains(query) || originalTitle.contains(query)) {
            return true;
        }

        String[] queryWords = query.trim().split("\\s+");
        for (String word : queryWords) {
            if (StringUtils.hasText(word) && word.length() >= 2) {
                if (title.contains(word) || originalTitle.contains(word) || summary.contains(word)) {
                    return true;
                }
            }
        }

        if (summary.contains(query) && summary.length() <= 240) {
            return true;
        }

        return false;
    }

    private boolean matchesIntentTerms(String query, MediaSearchHit hit) {
        if (!StringUtils.hasText(query) || hit == null || hit.getMedia() == null) {
            return true;
        }
        Integer mediaType = hit.getMedia().getType();
        boolean asksMovie = containsAny(query, "悬疑片", "电影", "影片", "片子")
                || (containsAny(query, "片") && !containsAny(query, "剧", "电视剧", "剧集", "连续剧"));
        if (asksMovie && mediaType != null && mediaType != 1) {
            return false;
        }

        String title = hit.getMedia().getTitle() == null ? "" : hit.getMedia().getTitle().toLowerCase();
        String originalTitle = hit.getMedia().getOriginalTitle() == null ? "" : hit.getMedia().getOriginalTitle().toLowerCase();
        String summary = hit.getMedia().getSummary() == null ? "" : hit.getMedia().getSummary().toLowerCase();

        boolean asksSuspense = containsAny(query, "悬疑", "惊悚", "推理", "犯罪", "烧脑");
        if (asksSuspense) {
            Double vectorScore = hit.getVectorScore();
            Double lexicalScore = hit.getLexicalScore();
            boolean scoreRelevant = (vectorScore != null && vectorScore >= 0.45d)
                || (lexicalScore != null && lexicalScore >= 0.35d);
            return scoreRelevant
                || containsAny(title, "悬疑", "惊悚", "推理", "犯罪", "烧脑")
                    || containsAny(originalTitle, "suspense", "thriller", "mystery", "crime")
                    || containsAny(summary, "悬疑", "惊悚", "推理", "犯罪", "节奏", "紧凑", "刺激", "谜", "案件", "梦境", "潜入", "反转", "真相");
        }
        return true;
    }

    private String buildPreciseHighlight(Media media, String query) {
        if (!StringUtils.hasText(media.getSummary())) {
            return media.getTitle();
        }
        String summary = media.getSummary();
        int index = summary.toLowerCase().indexOf(query.toLowerCase());
        if (index < 0) {
            return summary.substring(0, Math.min(summary.length(), 90));
        }
        int start = Math.max(0, index - 18);
        int end = Math.min(summary.length(), index + query.length() + 45);
        return summary.substring(start, end);
    }

    private List<MediaSearchHit> retrieveIntentFallbackHits(String rawQuery, AiSearchPlan plan, int size) {
        String combined = (defaultText(rawQuery, "") + " " + defaultText(plan == null ? null : plan.getNormalizedQuery(), "")).toLowerCase(Locale.ROOT);
        boolean asksSuspense = containsAny(combined, "悬疑", "惊悚", "推理", "犯罪", "烧脑");
        if (!asksSuspense) {
            return List.of();
        }

        LambdaQueryWrapper<Media> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Media::getDeleted, 0);
        wrapper.eq(Media::getType, 1);
        wrapper.and(q -> q.like(Media::getTitle, "悬疑")
                .or().like(Media::getSummary, "悬疑")
                .or().like(Media::getSummary, "惊悚")
                .or().like(Media::getSummary, "推理")
                .or().like(Media::getSummary, "犯罪")
                .or().like(Media::getSummary, "梦境")
                .or().like(Media::getSummary, "潜入")
                .or().like(Media::getSummary, "反转")
                .or().like(Media::getSummary, "真相")
                .or().like(Media::getSummary, "案件"));
        wrapper.orderByDesc(Media::getRating, Media::getReleaseDate);
        wrapper.last("limit " + Math.max(1, size));

        return mediaMapper.selectList(wrapper).stream()
                .map(media -> {
                    MediaSearchHit hit = new MediaSearchHit(media, 0.62d, buildPreciseHighlight(media, "悬疑"), "mysql_intent_fallback");
                    hit.setLexicalScore(0.62d);
                    hit.setRerankScore(0.62d);
                    return hit;
                })
                .collect(Collectors.toList());
    }

    @Override
    public String generateMediaSummary(Long mediaId, String originalSummary) {
        aiAccessGuardService.assertAllowed(AiPromptScene.MEDIA_SUMMARY.getCode());
        Media media = requireMedia(mediaId);
        String baseSummary = StringUtils.hasText(originalSummary) ? originalSummary : media.getSummary();
        if (!StringUtils.hasText(baseSummary)) {
            throw new BusinessException(400, "Summary source must not be empty");
        }

        String cacheKey = aiCacheService.buildKey("summary", mediaId + "|" + baseSummary);
        String cached = aiCacheService.get(cacheKey, String.class);
        if (cached != null) {
            return cached;
        }

        String result;
        try {
            result = aiFailoverService.execute(AiPromptScene.MEDIA_SUMMARY.getCode(), client -> client.prompt()
                    .system(aiPromptTemplateService.getTemplate(AiPromptScene.MEDIA_SUMMARY))
                    .user(spec -> spec.text("""
                            title: {title}
                            original_summary:
                            {summary}
                            """)
                            .param("title", media.getTitle())
                            .param("summary", baseSummary))
                    .options(aiPromptTemplateService.buildOptions(AiPromptScene.MEDIA_SUMMARY))
                    .call()
                    .content());
        } catch (Exception e) {
            log.warn("AI summary generation failed, fallback to local summary. mediaId={}", mediaId, e);
            result = fallbackSummary(media.getTitle(), baseSummary);
        }

        aiCacheService.put(cacheKey, result, Duration.ofSeconds(aiProperties.getCache().getSummaryTtlSeconds()));
        aiAuditRecordService.record(AiPromptScene.MEDIA_SUMMARY.getCode(), baseSummary, result);
        return result;
    }

    @Override
    public AiMediaQaVO answerMediaQuestion(AiMediaQuestionRequest request) {
        aiAccessGuardService.assertAllowed(AiPromptScene.MEDIA_QA.getCode());
        Media media = requireMedia(request.getMediaId());
        String conversationId = StringUtils.hasText(request.getConversationId())
                ? request.getConversationId()
                : "media-" + request.getMediaId();

        RagQaContext ragQaContext = ragQaContextService.buildMediaQaContext(media.getId(), media.getTitle(), request.getQuestion());

        String answer = aiFailoverService.execute(AiPromptScene.MEDIA_QA.getCode(), client -> client.prompt()
                .system(aiPromptTemplateService.getTemplate(AiPromptScene.MEDIA_QA))
                .advisors(
                        new SimpleLoggerAdvisor(),
                        new MessageChatMemoryAdvisor(chatMemory, conversationId, 10)
                )
                .function(
                        "loadMediaProfile",
                        "Load structured profile facts for the current media",
                        MediaAiToolService.MediaToolRequest.class,
                        mediaAiToolService::loadMediaProfile
                )
                .function(
                        "loadAudienceVoices",
                        "Load audience comments and reactions for the current media",
                        MediaAiToolService.MediaToolRequest.class,
                        mediaAiToolService::loadAudienceVoices
                )
                .function(
                        "loadMediaRagEvidence",
                        "Load semantically retrieved evidence chunks for the current media and question",
                        MediaAiToolService.MediaQuestionToolRequest.class,
                        mediaAiToolService::loadMediaRagEvidence
                )
                .function(
                    "loadSourceProviderFacts",
                    "Load persisted source/provider facts directly from DB for the current media",
                    MediaAiToolService.MediaToolRequest.class,
                    mediaAiToolService::loadSourceProviderFacts
                )
                .options(aiPromptTemplateService.buildOptions(AiPromptScene.MEDIA_QA))
                .user(spec -> spec.text("""
                        current_media: {title}
                        media_id: {mediaId}
                        user_question: {question}
                        retrieved_evidence_mode: {retrievalMode}
                        retrieved_evidence:
                        {evidence}

                        You must answer based on the tool output and retrieved evidence. If evidence is weak, say so explicitly.
                        """)
                        .param("title", media.getTitle())
                        .param("mediaId", request.getMediaId())
                        .param("question", request.getQuestion())
                        .param("retrievalMode", ragQaContext.getRetrievalMode())
                        .param("evidence", defaultText(ragQaContext.getContext(), "No additional retrieved evidence.")))
                .call()
                .content());

        AiMediaQaVO vo = new AiMediaQaVO();
        vo.setConversationId(conversationId);
        vo.setAnswer(answer);
        vo.setRetrievalMode(ragQaContext.getRetrievalMode());
        vo.setCitations(ragQaContext.getCitations());
        vo.setReferences(ragQaContext.getCitations().stream()
                .map(citation -> citation.getKnowledgeBaseCode() + ": " + compress(citation.getSnippet(), 72))
                .toList());
        aiAuditRecordService.record(AiPromptScene.MEDIA_QA.getCode(), request.getQuestion(), answer);
        return vo;
    }

    @Override
    public AiBannerCopyVO generateBannerCopy(AiBannerCopyRequest request) {
        aiAccessGuardService.assertAllowed(AiPromptScene.BANNER_COPY.getCode());
        String mediaTitle = request.getMediaId() == null ? "" : requireMedia(request.getMediaId()).getTitle();
        String cacheKey = aiCacheService.buildKey("banner", mediaTitle + "|" + request.getPositionType() + "|" + request.getTargetAudience());
        AiBannerCopyVO cached = aiCacheService.get(cacheKey, AiBannerCopyVO.class);
        if (cached != null) {
            return cached;
        }

        BeanOutputConverter<AiBannerCopyVO> converter = new BeanOutputConverter<>(AiBannerCopyVO.class);
        AiBannerCopyVO result;
        try {
            result = aiFailoverService.execute(AiPromptScene.BANNER_COPY.getCode(), client -> client.prompt()
                    .system(aiPromptTemplateService.getTemplate(AiPromptScene.BANNER_COPY))
                    .user(spec -> spec.text("""
                            media_title: {mediaTitle}
                            position_type: {positionType}
                            target_audience: {targetAudience}
                            campaign_goal: {campaignGoal}
                            extra_requirement: {extraRequirement}
                            output_format:
                            {format}
                            """)
                            .param("mediaTitle", defaultText(mediaTitle, "Featured release"))
                            .param("positionType", request.getPositionType())
                            .param("targetAudience", defaultText(request.getTargetAudience(), "general audience"))
                            .param("campaignGoal", defaultText(request.getCampaignGoal(), "improve click-through and conversion"))
                            .param("extraRequirement", defaultText(request.getExtraRequirement(), "Keep the copy concise and premium"))
                            .param("format", converter.getFormat()))
                    .options(aiPromptTemplateService.buildOptions(AiPromptScene.BANNER_COPY))
                    .call()
                    .entity(converter));
        } catch (Exception e) {
            log.warn("AI banner copy generation failed, fallback to local copy.", e);
            result = fallbackBannerCopy(mediaTitle, request.getPositionType());
        }

        aiCacheService.put(cacheKey, result, Duration.ofSeconds(aiProperties.getCache().getDefaultTtlSeconds()));
        aiAuditRecordService.record(AiPromptScene.BANNER_COPY.getCode(), mediaTitle + "|" + request.getPositionType(), result.getFullText());
        return result;
    }

    @Override
    public AiCommentAuditVO auditComment(AiCommentAuditRequest request) {
        aiAccessGuardService.assertAllowed(AiPromptScene.COMMENT_AUDIT.getCode());
        BeanOutputConverter<AiCommentAuditVO> converter = new BeanOutputConverter<>(AiCommentAuditVO.class);
        try {
            AiCommentAuditVO result = aiFailoverService.execute(AiPromptScene.COMMENT_AUDIT.getCode(), client -> client.prompt()
                    .system(aiPromptTemplateService.getTemplate(AiPromptScene.COMMENT_AUDIT))
                    .user(spec -> spec.text("""
                            media_id: {mediaId}
                            comment_id: {commentId}
                            content:
                            {content}
                            output_format:
                            {format}
                            """)
                            .param("mediaId", request.getMediaId() == null ? "" : request.getMediaId())
                            .param("commentId", request.getCommentId() == null ? "" : request.getCommentId())
                            .param("content", request.getContent())
                            .param("format", converter.getFormat()))
                    .options(aiPromptTemplateService.buildOptions(AiPromptScene.COMMENT_AUDIT))
                    .call()
                    .entity(converter));
            aiAuditRecordService.record(AiPromptScene.COMMENT_AUDIT.getCode(), request.getContent(), result.getReason());
            return result;
        } catch (Exception e) {
            log.warn("AI comment audit failed, fallback to rule-based audit.", e);
            return fallbackCommentAudit(request.getContent());
        }
    }

    private AiSearchPlan buildSearchPlan(String query, int page, int pageSize) {
        BeanOutputConverter<AiSearchPlan> converter = new BeanOutputConverter<>(AiSearchPlan.class);
        try {
            AiSearchPlan plan = aiFailoverService.execute(AiPromptScene.MEDIA_SEARCH_PLAN.getCode(), client -> client.prompt()
                    .system(aiPromptTemplateService.getTemplate(AiPromptScene.MEDIA_SEARCH_PLAN))
                    .user(spec -> spec.text("""
                            user_query: {query}
                            page: {page}
                            page_size: {pageSize}
                            output_format:
                            {format}
                            """)
                            .param("query", query)
                            .param("page", page)
                            .param("pageSize", pageSize)
                            .param("format", converter.getFormat()))
                    .options(aiPromptTemplateService.buildOptions(AiPromptScene.MEDIA_SEARCH_PLAN))
                    .call()
                    .entity(converter));
            return normalizePlan(query, page, pageSize, plan);
        } catch (Exception e) {
            log.warn("AI search plan parse failed, fallback to keyword search. query={}", query, e);
            AiSearchPlan fallback = new AiSearchPlan();
            fallback.setNormalizedQuery(query.trim());
            fallback.setSemanticQuery(query.trim());
            fallback.setIntentSummary("Fallback keyword search plan");
            fallback.setType(0);
            fallback.setStatus(null);
            fallback.setSortBy("default");
            return normalizePlan(query, page, pageSize, fallback);
        }
    }

    private AiSearchPlan normalizePlan(String query, int page, int pageSize, AiSearchPlan plan) {
        if (plan == null) {
            plan = new AiSearchPlan();
        }
        if (plan.getType() == null || plan.getType() < 0 || plan.getType() > 3) {
            plan.setType(0);
        }
        if (!StringUtils.hasText(plan.getIntentSummary())) {
            plan.setIntentSummary("Search plan inferred from user intent");
        }
        if (!StringUtils.hasText(plan.getSortBy())) {
            plan.setSortBy("default");
        }
        if (!StringUtils.hasText(plan.getNormalizedQuery())) {
            plan.setNormalizedQuery(query.trim());
        }
        if (!StringUtils.hasText(plan.getSemanticQuery())) {
            plan.setSemanticQuery(defaultText(plan.getNormalizedQuery(), query.trim()));
        }
        applyIntentHeuristics(plan, query);
        if (!StringUtils.hasText(plan.getQueryMode())) {
            plan.setQueryMode(resolveQueryMode(plan, query).name());
        }
        plan.setPage(page);
        plan.setPageSize(pageSize);
        return plan;
    }

    private AiQueryMode resolveQueryMode(AiSearchPlan plan, String rawQuery) {
        if (plan != null && StringUtils.hasText(plan.getQueryMode())) {
            try {
                return AiQueryMode.valueOf(plan.getQueryMode());
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid query mode from model output.
            }
        }
        return AiSearchQueryHelper.classify(
                rawQuery,
                plan == null ? null : plan.getNormalizedQuery()
        );
    }

    private List<MediaSearchHit> retryRetrieveWithRefinedKeywords(String rawQuery, AiSearchPlan plan, int size) {
        for (String retryKeyword : buildRetryKeywords(rawQuery, plan)) {
            TvboxSyncResult syncResult = tvboxSyncCacheService.syncIfNotCached(retryKeyword, Math.max(size * 3, 24));
            if (syncResult.getSyncedExternalResourceCount() <= 0) {
                continue;
            }
            log.info("AI search retry ingest completed. rawQuery={}, retryKeyword={}, externalCount={}, affectedMediaCount={}",
                    rawQuery,
                    retryKeyword,
                    syncResult.getSyncedExternalResourceCount(),
                    syncResult.getAffectedMediaIds().size());
            List<MediaSearchHit> retriedHits = hybridMediaRetrievalService.retrieve(plan, size);
            if (!retriedHits.isEmpty()) {
                return retriedHits;
            }
        }
        return List.of();
    }

    private List<String> buildRetryKeywords(String rawQuery, AiSearchPlan plan) {
        Set<String> keywords = new LinkedHashSet<>();
        String normalized = plan == null ? null : plan.getNormalizedQuery();
        if (StringUtils.hasText(normalized)) {
            String candidate = normalized.trim();
            if (!candidate.equalsIgnoreCase(rawQuery == null ? "" : rawQuery.trim()) && candidate.length() >= 2) {
                keywords.add(candidate);
            }
        }

        String combined = (defaultText(rawQuery, "") + " " + defaultText(normalized, "")).toLowerCase(Locale.ROOT);
        if (containsAny(combined, "放松", "轻松", "治愈", "解压", "下班")) {
            keywords.add("轻松");
        }
        if ((plan != null && Integer.valueOf(2).equals(plan.getType())) || containsAny(combined, "电视剧", "剧集", "连续剧", "追剧", "剧")) {
            keywords.add("电视剧");
        }
        if ((plan != null && Integer.valueOf(3).equals(plan.getType())) || containsAny(combined, "动画", "动漫", "番")) {
            keywords.add("动画");
        }
        if ((plan != null && Integer.valueOf(1).equals(plan.getType())) || containsAny(combined, "电影", "影片", "片子")) {
            keywords.add("电影");
        }
        if (containsAny(combined, "悬疑", "惊悚", "推理", "犯罪", "烧脑")) {
            keywords.add("悬疑");
        }
        return new ArrayList<>(keywords);
    }

    private void applyIntentHeuristics(AiSearchPlan plan, String rawQuery) {
        if (plan == null) {
            return;
        }
        String raw = defaultText(rawQuery, "").trim();
        String normalized = defaultText(plan.getNormalizedQuery(), "").trim();
        String combined = (raw + " " + normalized).toLowerCase(Locale.ROOT);

        if (containsAny(combined, "动画", "动漫", "番")) {
            plan.setType(3);
        } else if (containsAny(combined, "悬疑", "惊悚", "推理", "犯罪", "烧脑")
                && !containsAny(combined, "电视剧", "剧集", "连续剧", "追剧", "动画", "动漫")) {
            if (plan.getType() == null || plan.getType() == 0) {
                plan.setType(1);
            }
        } else if ((containsAny(combined, "悬疑片", "电影", "影片", "片子")
                || (containsAny(combined, "片") && !containsAny(combined, "剧", "电视剧", "剧集", "连续剧")))) {
            if (plan.getType() == null || plan.getType() == 0) {
                plan.setType(1);
            }
        } else if (containsAny(combined, "电视剧", "剧集", "连续剧", "追剧")
                || (containsAny(combined, "剧") && !containsAny(combined, "喜剧", "悲剧", "话剧"))) {
            if (plan.getType() == null || plan.getType() == 0) {
                plan.setType(2);
            }
        }

        String refinedKeyword = null;
        if (containsAny(combined, "喜剧", "搞笑", "轻喜")) {
            refinedKeyword = "喜剧";
        } else if (containsAny(combined, "悬疑", "惊悚", "推理", "犯罪", "烧脑")) {
            refinedKeyword = "悬疑";
        } else if (containsAny(combined, "放松", "轻松", "治愈", "解压", "下班")) {
            refinedKeyword = Integer.valueOf(2).equals(plan.getType()) ? "轻松" : "治愈";
        }

        boolean looksLikeConversational = !StringUtils.hasText(normalized)
                || normalized.length() > 12
                || containsAny(normalized, "推荐", "适合", "来点", "找一部", "想看", "有没有");
        if (looksLikeConversational && StringUtils.hasText(refinedKeyword)) {
            plan.setNormalizedQuery(refinedKeyword);
        }

        if (!StringUtils.hasText(plan.getSemanticQuery()) || looksLikeConversational) {
            String semantic = StringUtils.hasText(plan.getNormalizedQuery()) ? plan.getNormalizedQuery() : raw;
            if (containsAny(combined, "放松", "轻松", "治愈", "解压", "下班")) {
                semantic = semantic + " 下班后 放松";
            }
            if (containsAny(combined, "悬疑", "惊悚", "推理", "犯罪", "烧脑", "节奏快", "紧凑", "刺激")) {
                semantic = semantic + " 悬疑 节奏快";
            }
            if (Integer.valueOf(2).equals(plan.getType()) && !semantic.contains("电视剧")) {
                semantic = semantic + " 电视剧";
            }
            plan.setSemanticQuery(semantic.trim());
        }
    }

    private boolean containsAny(String text, String... terms) {
        if (!StringUtils.hasText(text) || terms == null || terms.length == 0) {
            return false;
        }
        for (String term : terms) {
            if (StringUtils.hasText(term) && text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String buildSearchAnswer(String query, AiSearchPlan searchPlan, List<MediaSearchHit> hits) {
        if (hits.isEmpty()) {
            return "当前片库里没有找到高匹配结果，建议换更具体的片名或补充题材关键词再试。";
        }

        String context = hits.stream()
                .limit(5)
                .map(hit -> {
                    Media media = hit.getMedia();
                    return String.format(Locale.ROOT,
                            "- %s | type=%s | status=%s | rating=%s | source=%s | clue=%s",
                            media.getTitle(),
                            media.getType(),
                            media.getStatus(),
                            media.getRating(),
                            hit.getSource(),
                            defaultText(hit.getHighlight(), defaultText(media.getSummary(), media.getTitle()))
                    );
                })
                .collect(Collectors.joining("\n"));

        try {
            return aiFailoverService.execute(AiPromptScene.MEDIA_SEARCH_ANSWER.getCode(), client -> client.prompt()
                    .system(aiPromptTemplateService.getTemplate(AiPromptScene.MEDIA_SEARCH_ANSWER))
                    .user(spec -> spec.text("""
                            user_query: {query}
                            intent_summary: {intent}
                            retrieval_context:
                            {context}
                            """)
                            .param("query", query)
                            .param("intent", searchPlan.getIntentSummary())
                            .param("context", context))
                    .options(aiPromptTemplateService.buildOptions(AiPromptScene.MEDIA_SEARCH_ANSWER))
                    .call()
                    .content());
        } catch (Exception e) {
            log.warn("AI search answer generation failed, fallback to local answer. query={}", query, e);
            return "已命中 " + hits.size() + " 条结果，当前最相关的是《" + hits.get(0).getMedia().getTitle() + "》。";
        }
    }

    private List<AiCitationVO> buildSearchCitations(List<MediaSearchHit> hits) {
        return hits.stream()
                .limit(5)
                .map(hit -> {
                    AiCitationVO citation = new AiCitationVO();
                    citation.setMediaId(hit.getMedia().getId());
                    citation.setTitle(hit.getMedia().getTitle());
                    citation.setSnippet(defaultText(hit.getHighlight(), defaultText(hit.getMedia().getSummary(), hit.getMedia().getTitle())));
                    citation.setSource(hit.getSource());
                    citation.setKnowledgeBaseCode(hit.getKnowledgeBaseCode());
                    citation.setScore(hit.getRerankScore() != null ? hit.getRerankScore() : hit.getScore());
                    return citation;
                })
                .collect(Collectors.toList());
    }

    private String fallbackSummary(String title, String originalSummary) {
        String compact = originalSummary.replaceAll("\\s+", " ").trim();
        if (compact.length() > 100) {
            compact = compact.substring(0, 100);
        }
        return title + " focuses on a clear central conflict and a watchable emotional arc."
                + (StringUtils.hasText(compact) ? " " + compact : "");
    }

    private AiBannerCopyVO fallbackBannerCopy(String mediaTitle, String positionType) {
        AiBannerCopyVO vo = new AiBannerCopyVO();
        String safeTitle = StringUtils.hasText(mediaTitle) ? mediaTitle : "Featured Release";
        vo.setTitle(safeTitle.length() > 12 ? safeTitle.substring(0, 12) : safeTitle);
        vo.setSubtitle("Discover a standout title in " + defaultText(positionType, "the hero slot"));
        vo.setCta("View Now");
        vo.setFullText(vo.getTitle() + " | " + vo.getSubtitle());
        return vo;
    }

    private AiCommentAuditVO fallbackCommentAudit(String content) {
        AiCommentAuditVO vo = new AiCommentAuditVO();
        boolean risky = content.contains("垃圾") || content.contains("滚") || content.contains("傻");
        vo.setPass(!risky);
        vo.setRiskLevel(risky ? 78 : 12);
        vo.setSuggestion(risky ? "Send to manual review" : "Approve directly");
        vo.setReason(risky ? "Detected insulting or abusive wording" : "No obvious abuse or spoiler risk detected");
        return vo;
    }

    private Media requireMedia(Long mediaId) {
        Media media = mediaMapper.selectById(mediaId);
        if (media == null || media.getDeleted() == 1) {
            throw new BusinessException(404, "Media does not exist");
        }
        return media;
    }

    private void logSearchKeyword(String query) {
        try {
            SearchKeywordLog record = new SearchKeywordLog();
            record.setKeyword(query.trim());
            record.setSearchTime(LocalDateTime.now());
            searchKeywordLogMapper.insert(record);
        } catch (Exception e) {
            log.warn("Failed to persist search keyword log, query={}", query, e);
        }
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String compress(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, maxLength) + "...";
    }
}
