package com.jelly.cinema.service.ai.rag;

import com.jelly.cinema.common.config.property.RagProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class HashingRagEmbeddingService implements RagEmbeddingService {

    private final RagProperties ragProperties;

    @Override
    public List<Float> embed(String text) {
        int dimension = ragProperties.getEmbedding().getDimension();
        float[] vector = new float[dimension];
        List<String> features = buildFeatures(text);

        if (features.isEmpty()) {
            return zeros(dimension);
        }

        for (String feature : features) {
            int index = Math.floorMod(feature.hashCode(), dimension);
            float weight = feature.length() > 1 ? 1.15f : 0.8f;
            vector[index] += weight;
        }

        normalize(vector);
        List<Float> values = new ArrayList<>(dimension);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    private List<String> buildFeatures(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic} ]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }

        List<String> features = new ArrayList<>();
        String[] words = normalized.split(" ");
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            features.add(word);
            if (word.length() > 2) {
                for (int i = 0; i < word.length() - 1; i++) {
                    features.add(word.substring(i, i + 2));
                }
            }
        }

        String compact = normalized.replace(" ", "");
        for (int i = 0; i < compact.length(); i++) {
            features.add(String.valueOf(compact.charAt(i)));
            if (i < compact.length() - 1) {
                features.add(compact.substring(i, i + 2));
            }
            if (i < compact.length() - 2) {
                features.add(compact.substring(i, i + 3));
            }
        }
        return features;
    }

    private void normalize(float[] vector) {
        double norm = 0.0d;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm <= 0.0d) {
            return;
        }
        float scale = (float) (1.0d / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] * scale;
        }
    }

    private List<Float> zeros(int dimension) {
        List<Float> values = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            values.add(0.0f);
        }
        return values;
    }
}
