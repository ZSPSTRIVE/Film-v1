package com.jelly.cinema.service.ai;

import com.jelly.cinema.model.entity.Media;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MediaSearchHit {

    private Media media;

    private Double score;

    private String highlight;

    private String source;

    private Double lexicalScore;

    private String lexicalSource;

    private Double vectorScore;

    private Double rerankScore;

    private String knowledgeBaseCode;

    public MediaSearchHit(Media media, Double score, String highlight, String source) {
        this.media = media;
        this.score = score;
        this.highlight = highlight;
        this.source = source;
    }
}
