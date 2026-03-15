package com.jelly.cinema.service;

import com.jelly.cinema.service.ai.AiPromptScene;
import org.springframework.ai.openai.OpenAiChatOptions;

public interface AiPromptTemplateService {

    String getTemplate(AiPromptScene scene);

    OpenAiChatOptions buildOptions(AiPromptScene scene);
}
