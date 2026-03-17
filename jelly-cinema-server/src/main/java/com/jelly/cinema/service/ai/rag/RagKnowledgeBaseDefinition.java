package com.jelly.cinema.service.ai.rag;

import com.jelly.cinema.common.config.property.RagProperties;
import lombok.Getter;

@Getter
public enum RagKnowledgeBaseDefinition {

    MEDIA_PROFILE(
            "media_profile_kb",
            "Media Profile KB",
            "media",
            "media",
            "Structured media profile and summary knowledge"
    ),
    COMMENT_QA(
            "comment_qa_kb",
            "Comment QA KB",
            "comment",
            "comment",
            "Audience comments and sentiment evidence knowledge"
    ),
    EXTERNAL_MEDIA(
            "external_media_kb",
            "External Media KB",
            "external_media",
            "media",
            "External provider metadata and availability knowledge"
    );

    private final String code;
    private final String name;
    private final String domain;
    private final String bizType;
    private final String description;

    RagKnowledgeBaseDefinition(String code, String name, String domain, String bizType, String description) {
        this.code = code;
        this.name = name;
        this.domain = domain;
        this.bizType = bizType;
        this.description = description;
    }

    public String collectionName(RagProperties ragProperties) {
        if (this == MEDIA_PROFILE) {
            return ragProperties.getCollections().getMediaProfile();
        }
        if (this == COMMENT_QA) {
            return ragProperties.getCollections().getCommentQa();
        }
        return ragProperties.getCollections().getExternalMedia();
    }
}
