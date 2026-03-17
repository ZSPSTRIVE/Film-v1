package com.jelly.cinema.service.ai.rag;

import com.jelly.cinema.common.config.property.RagProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RagChunkingService {

    private final RagProperties ragProperties;

    public List<String> split(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        String normalized = text.replace("\r", "\n")
                .replaceAll("\\n{2,}", "\n")
                .replaceAll("[ \\t]+", " ")
                .trim();
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }

        int chunkSize = Math.max(120, ragProperties.getChunking().getChunkSize());
        int overlap = Math.max(0, Math.min(chunkSize / 2, ragProperties.getChunking().getChunkOverlap()));

        List<String> sentences = new ArrayList<>();
        for (String part : normalized.split("(?<=[。！？!?；;\\n])")) {
            String sentence = part.trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        if (sentences.isEmpty()) {
            sentences = List.of(normalized);
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (current.length() == 0) {
                current.append(sentence);
                continue;
            }
            if (current.length() + 1 + sentence.length() <= chunkSize) {
                current.append('\n').append(sentence);
                continue;
            }
            chunks.add(current.toString().trim());
            String tail = overlapTail(current.toString(), overlap);
            current = new StringBuilder(tail);
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(sentence);
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private String overlapTail(String text, int overlap) {
        if (text.length() <= overlap) {
            return text;
        }
        return text.substring(text.length() - overlap);
    }
}
