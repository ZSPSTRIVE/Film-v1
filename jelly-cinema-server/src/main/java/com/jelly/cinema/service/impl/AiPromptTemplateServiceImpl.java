package com.jelly.cinema.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.mapper.AiPromptTemplateMapper;
import com.jelly.cinema.model.entity.AiPromptTemplateEntity;
import com.jelly.cinema.service.AiPromptTemplateService;
import com.jelly.cinema.service.ai.AiPromptScene;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiPromptTemplateServiceImpl implements AiPromptTemplateService {

    private final AiPromptTemplateMapper aiPromptTemplateMapper;

    @Override
    public String getTemplate(AiPromptScene scene) {
        try {
            AiPromptTemplateEntity template = aiPromptTemplateMapper.selectOne(new LambdaQueryWrapper<AiPromptTemplateEntity>()
                    .eq(AiPromptTemplateEntity::getSceneCode, scene.getCode())
                    .last("limit 1"));
            if (template != null && StringUtils.hasText(template.getTemplateContent())) {
                return template.getTemplateContent();
            }
        } catch (Exception ignored) {
            // Ignore DB failures and fall back to built-in prompts.
        }
        return scene.getDefaultTemplate();
    }

    @Override
    public OpenAiChatOptions buildOptions(AiPromptScene scene) {
        Double temperature = scene.getDefaultTemperature();
        try {
            AiPromptTemplateEntity template = aiPromptTemplateMapper.selectOne(new LambdaQueryWrapper<AiPromptTemplateEntity>()
                    .eq(AiPromptTemplateEntity::getSceneCode, scene.getCode())
                    .last("limit 1"));
            if (template != null && template.getTemperature() != null) {
                temperature = template.getTemperature().doubleValue();
            }
        } catch (Exception ignored) {
            // Ignore DB failures and keep defaults.
        }

        return OpenAiChatOptions.builder()
                .temperature(temperature)
                .maxTokens(scene.getDefaultMaxTokens())
                .build();
    }
}
