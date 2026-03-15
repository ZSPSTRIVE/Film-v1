package com.jelly.cinema.service.ai;

import java.util.List;

public interface MediaRetrievalService {

    List<MediaSearchHit> retrieve(AiSearchPlan plan, int size);
}
