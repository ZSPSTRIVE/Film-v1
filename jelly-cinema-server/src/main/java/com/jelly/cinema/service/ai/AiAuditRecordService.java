package com.jelly.cinema.service.ai;

import com.jelly.cinema.mapper.AiGenerateRecordMapper;
import com.jelly.cinema.model.entity.AiGenerateRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAuditRecordService {

    private final AiGenerateRecordMapper aiGenerateRecordMapper;

    public void record(String sceneCode, String originalContent, String generatedContent) {
        try {
            AiGenerateRecord record = new AiGenerateRecord();
            record.setSceneCode(sceneCode);
            record.setOriginalContent(truncate(originalContent, 4000));
            record.setGeneratedContent(truncate(generatedContent, 4000));
            aiGenerateRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("Failed to persist AI generate record, sceneCode={}", sceneCode, e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
