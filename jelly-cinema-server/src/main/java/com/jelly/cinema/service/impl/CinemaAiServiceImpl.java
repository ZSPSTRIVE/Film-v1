package com.jelly.cinema.service.impl;

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
import com.jelly.cinema.model.vo.AiCommentAuditVO;
import com.jelly.cinema.model.vo.AiMediaQaVO;
import com.jelly.cinema.model.vo.AiSearchVO;
import com.jelly.cinema.service.AiFailoverService;
import com.jelly.cinema.service.AiPromptTemplateService;
import com.jelly.cinema.service.CinemaAiService;
import com.jelly.cinema.service.ai.AiAccessGuardService;
import com.jelly.cinema.service.ai.AiAuditRecordService;
import com.jelly.cinema.service.ai.AiCacheService;
import com.jelly.cinema.service.ai.AiPromptScene;
import com.jelly.cinema.service.ai.AiSearchPlan;
import com.jelly.cinema.service.ai.HybridMediaRetrievalService;
import com.jelly.cinema.service.ai.MediaAiToolService;
import com.jelly.cinema.service.ai.MediaSearchHit;
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
import java.util.List;
import java.util.Locale;
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

        String cacheKey = aiCacheService.buildKey("search", query + "|" + currentPage + "|" + currentPageSize);
        AiSearchVO cached = aiCacheService.get(cacheKey, AiSearchVO.class);
        if (cached != null) {
            return cached;
        }

        AiSearchPlan searchPlan = buildSearchPlan(query, currentPage, currentPageSize);
        List<MediaSearchHit> hits = hybridMediaRetrievalService.retrieve(searchPlan, currentPageSize);
        if (hits.isEmpty() && StringUtils.hasText(query) && !query.equals(searchPlan.getNormalizedQuery())) {
            searchPlan.setNormalizedQuery(query.trim());
            hits = hybridMediaRetrievalService.retrieve(searchPlan, currentPageSize);
        }

        AiSearchVO vo = new AiSearchVO();
        vo.setMediaList(hits.stream().map(MediaSearchHit::getMedia).collect(Collectors.toList()));
        vo.setMatchedCount(vo.getMediaList().size());
        vo.setNormalizedQuery(searchPlan.getNormalizedQuery());
        vo.setIntentSummary(searchPlan.getIntentSummary());
        vo.setRetrievalMode(hits.isEmpty() ? "none" : hits.get(0).getSource());
        vo.setAnswer(buildSearchAnswer(query, searchPlan, hits));

        aiCacheService.put(cacheKey, vo, Duration.ofSeconds(aiProperties.getCache().getSearchTtlSeconds()));
        aiAuditRecordService.record(AiPromptScene.MEDIA_SEARCH_ANSWER.getCode(), query, vo.getAnswer());
        return vo;
    }

    @Override
    public String generateMediaSummary(Long mediaId, String originalSummary) {
        aiAccessGuardService.assertAllowed(AiPromptScene.MEDIA_SUMMARY.getCode());
        Media media = requireMedia(mediaId);
        String baseSummary = StringUtils.hasText(originalSummary) ? originalSummary : media.getSummary();
        if (!StringUtils.hasText(baseSummary)) {
            throw new BusinessException(400, "当前影视缺少可用于润色的简介");
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
                            影片名称：{title}
                            原始简介：
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

        String answer = aiFailoverService.execute(AiPromptScene.MEDIA_QA.getCode(), client -> client.prompt()
                .system(aiPromptTemplateService.getTemplate(AiPromptScene.MEDIA_QA))
                .advisors(
                        new SimpleLoggerAdvisor(),
                        new MessageChatMemoryAdvisor(chatMemory, conversationId, 10)
                )
                .function(
                        "loadMediaProfile",
                        "加载当前影片的标题、简介、类型、状态、评分等资料",
                        MediaAiToolService.MediaToolRequest.class,
                        mediaAiToolService::loadMediaProfile
                )
                .function(
                        "loadAudienceVoices",
                        "加载当前影片的热门评论和观众反馈摘要",
                        MediaAiToolService.MediaToolRequest.class,
                        mediaAiToolService::loadAudienceVoices
                )
                .options(aiPromptTemplateService.buildOptions(AiPromptScene.MEDIA_QA))
                .user(spec -> spec.text("""
                        当前影片：{title}
                        mediaId={mediaId}
                        用户问题：{question}
                        如果需要事实依据，请先调用函数再回答。
                        """)
                        .param("title", media.getTitle())
                        .param("mediaId", request.getMediaId())
                        .param("question", request.getQuestion()))
                .call()
                .content());

        AiMediaQaVO vo = new AiMediaQaVO();
        vo.setConversationId(conversationId);
        vo.setAnswer(answer);
        vo.setReferences(List.of("影片资料", "社区评论"));
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
                            影片名称：{mediaTitle}
                            运营位：{positionType}
                            目标人群：{targetAudience}
                            活动目标：{campaignGoal}
                            额外要求：{extraRequirement}
                            输出格式：
                            {format}
                            """)
                            .param("mediaTitle", defaultText(mediaTitle, "本周精选"))
                            .param("positionType", request.getPositionType())
                            .param("targetAudience", defaultText(request.getTargetAudience(), "大众用户"))
                            .param("campaignGoal", defaultText(request.getCampaignGoal(), "提升点击与转化"))
                            .param("extraRequirement", defaultText(request.getExtraRequirement(), "保持简洁、克制、有高级感"))
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
                            影片ID：{mediaId}
                            评论ID：{commentId}
                            评论内容：
                            {content}
                            输出格式：
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
                            用户输入：{query}
                            page={page}
                            pageSize={pageSize}
                            输出格式：
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
            fallback.setIntentSummary("直接按关键词检索");
            fallback.setType(0);
            fallback.setStatus(null);
            fallback.setSortBy("default");
            fallback.setPage(page);
            fallback.setPageSize(pageSize);
            return fallback;
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
            plan.setIntentSummary("根据用户描述生成的影视检索计划");
        }
        if (!StringUtils.hasText(plan.getSortBy())) {
            plan.setSortBy("default");
        }
        if (!StringUtils.hasText(plan.getNormalizedQuery())) {
            plan.setNormalizedQuery(query.trim());
        }
        plan.setPage(page);
        plan.setPageSize(pageSize);
        return plan;
    }

    private String buildSearchAnswer(String query, AiSearchPlan searchPlan, List<MediaSearchHit> hits) {
        if (hits.isEmpty()) {
            return "当前片库里没找到完全匹配的内容，建议换成更具体的片名、题材或上映状态再试一次。";
        }

        String context = hits.stream()
                .limit(5)
                .map(hit -> {
                    Media media = hit.getMedia();
                    return String.format(Locale.ROOT,
                            "- %s | 类型=%s | 状态=%s | 评分=%s | 线索=%s",
                            media.getTitle(),
                            media.getType(),
                            media.getStatus(),
                            media.getRating(),
                            defaultText(hit.getHighlight(), defaultText(media.getSummary(), media.getTitle()))
                    );
                })
                .collect(Collectors.joining("\n"));

        try {
            return aiFailoverService.execute(AiPromptScene.MEDIA_SEARCH_ANSWER.getCode(), client -> client.prompt()
                    .system(aiPromptTemplateService.getTemplate(AiPromptScene.MEDIA_SEARCH_ANSWER))
                    .user(spec -> spec.text("""
                            用户问题：{query}
                            检索计划：{intent}
                            召回结果：
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
            return "已结合你的描述筛出 " + hits.size() + " 部内容，当前最值得先看的候选是《" + hits.get(0).getMedia().getTitle() + "》。";
        }
    }

    private String fallbackSummary(String title, String originalSummary) {
        String compact = originalSummary.replaceAll("\\s+", " ").trim();
        if (compact.length() > 100) {
            compact = compact.substring(0, 100);
        }
        return "《" + title + "》围绕核心人物与主要冲突展开，整体节奏清晰、看点集中，适合先从这段故事切入了解作品魅力。"
                + (StringUtils.hasText(compact) ? " " + compact : "");
    }

    private AiBannerCopyVO fallbackBannerCopy(String mediaTitle, String positionType) {
        AiBannerCopyVO vo = new AiBannerCopyVO();
        String safeTitle = StringUtils.hasText(mediaTitle) ? mediaTitle : "本周精选";
        vo.setTitle(safeTitle.length() > 12 ? safeTitle.substring(0, 12) : safeTitle);
        vo.setSubtitle("在 " + defaultText(positionType, "首页焦点位") + " 重新发现值得马上打开的内容");
        vo.setCta("立即查看");
        vo.setFullText(vo.getTitle() + "｜" + vo.getSubtitle());
        return vo;
    }

    private AiCommentAuditVO fallbackCommentAudit(String content) {
        AiCommentAuditVO vo = new AiCommentAuditVO();
        boolean risky = content.contains("垃圾") || content.contains("滚") || content.contains("傻");
        vo.setPass(!risky);
        vo.setRiskLevel(risky ? 78 : 12);
        vo.setSuggestion(risky ? "建议进入人工复核队列" : "建议直接放行");
        vo.setReason(risky ? "命中了明显的人身攻击或辱骂词汇" : "未发现明显违规词，语义风险较低");
        return vo;
    }

    private Media requireMedia(Long mediaId) {
        Media media = mediaMapper.selectById(mediaId);
        if (media == null || media.getDeleted() == 1) {
            throw new BusinessException(404, "影视内容不存在");
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
}
