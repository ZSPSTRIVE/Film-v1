package com.jelly.cinema.service.ai.rag;

import com.jelly.cinema.common.config.property.RagProperties;
import com.jelly.cinema.model.vo.AiCitationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagQaContextService {

    private final RagProperties ragProperties;
    private final RagEmbeddingService ragEmbeddingService;
    private final RagMilvusService ragMilvusService;

    public RagQaContext buildMediaQaContext(Long mediaId, String mediaTitle, String question) {
        if (!ragProperties.isEnable() || mediaId == null || question == null || question.isBlank()) {
            return empty();
        }

        try {
            List<Float> vector = ragEmbeddingService.embed(question);
            int topK = Math.max(2, ragProperties.getRetrieval().getQaTopK());

            List<RagVectorHit> hits = new ArrayList<>();
            hits.addAll(search(mediaId, vector, topK, RagKnowledgeBaseDefinition.MEDIA_PROFILE));
            hits.addAll(search(mediaId, vector, topK, RagKnowledgeBaseDefinition.COMMENT_QA));
            hits.addAll(search(mediaId, vector, topK, RagKnowledgeBaseDefinition.EXTERNAL_MEDIA));

            if (hits.isEmpty()) {
                return empty();
            }

            List<RagVectorHit> sorted = hits.stream()
                    .sorted(Comparator.comparing(RagVectorHit::getScore, Comparator.nullsLast(Double::compareTo)).reversed())
                    .limit(ragProperties.getRetrieval().getMaxCitations())
                    .collect(Collectors.toList());

            List<AiCitationVO> citations = sorted.stream()
                    .map(hit -> toCitation(hit, mediaTitle))
                    .toList();

            String context = sorted.stream()
                    .map(hit -> "- [" + hit.getKnowledgeBaseCode() + "] " + hit.getChunkText())
                    .collect(Collectors.joining("\n"));

            String retrievalMode = sorted.stream()
                    .map(RagVectorHit::getKnowledgeBaseCode)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining("+", "milvus:", ""));

            return RagQaContext.builder()
                    .retrievalMode(retrievalMode)
                    .context(context)
                    .citations(citations)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to build RAG QA context. mediaId={}", mediaId, e);
            return empty();
        }
    }

    private List<RagVectorHit> search(Long mediaId,
                                      List<Float> vector,
                                      int topK,
                                      RagKnowledgeBaseDefinition definition) {
        return ragMilvusService.search(
                definition.collectionName(ragProperties),
                definition.getCode(),
                vector,
                "biz_id == " + mediaId,
                topK
        );
    }

    private AiCitationVO toCitation(RagVectorHit hit, String mediaTitle) {
        AiCitationVO citation = new AiCitationVO();
        citation.setMediaId(hit.getBizId());
        citation.setTitle(hit.getTitle() == null || hit.getTitle().isBlank() ? mediaTitle : hit.getTitle());
        citation.setSnippet(hit.getChunkText());
        citation.setSource(RagKnowledgeBaseDefinition.EXTERNAL_MEDIA.getCode().equals(hit.getKnowledgeBaseCode())
                ? "milvus_external"
                : "milvus_vector");
        citation.setKnowledgeBaseCode(hit.getKnowledgeBaseCode());
        citation.setScore(hit.getScore());
        return citation;
    }

    private RagQaContext empty() {
        return RagQaContext.builder()
                .retrievalMode("tool_only")
                .context("")
                .citations(List.of())
                .build();
    }
}
