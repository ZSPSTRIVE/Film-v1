package com.jelly.cinema.service.ai;

import com.jelly.cinema.model.entity.Media;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaSearchHit {

    private Media media;

    private Double score;

    private String highlight;

    private String source;
}
