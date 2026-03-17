package com.jelly.cinema.service.ai.rag;

import java.util.List;

public interface RagEmbeddingService {

    List<Float> embed(String text);

    default List<List<Float>> embedAll(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
