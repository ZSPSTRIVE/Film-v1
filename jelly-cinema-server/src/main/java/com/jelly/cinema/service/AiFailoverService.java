package com.jelly.cinema.service;

import com.jelly.cinema.common.config.property.AiProperties;
import com.jelly.cinema.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiFailoverService {

    private final AiProperties aiProperties;
    private final Map<String, ChatModel> chatModels = new ConcurrentHashMap<>();
    private final Map<String, ChatClient> chatClients = new ConcurrentHashMap<>();
    private final Map<String, Integer> failureCounts = new ConcurrentHashMap<>();
    private List<AiProperties.Provider> activeProviders;

    public AiFailoverService(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        initModels();
    }

    private void initModels() {
        if (!aiProperties.isEnable() || aiProperties.getProviders() == null) {
            log.warn("AI configurations are disabled or providers are absent.");
            return;
        }

        this.activeProviders = aiProperties.getProviders().stream()
                .filter(AiProperties.Provider::isEnabled)
                .sorted(Comparator.comparingInt(AiProperties.Provider::getPriority))
                .collect(Collectors.toList());

        for (AiProperties.Provider provider : activeProviders) {
            try {
                OpenAiApi openAiApi = new OpenAiApi(provider.getBaseUrl(), provider.getApiKey());
                OpenAiChatOptions options = OpenAiChatOptions.builder()
                        .model(provider.getModel())
                        .temperature(provider.getTemperature())
                        .maxTokens(provider.getMaxTokens())
                        .build();

                OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, options);
                chatModels.put(provider.getName(), chatModel);
                chatClients.put(provider.getName(), ChatClient.create(chatModel));
                failureCounts.put(provider.getName(), 0);
                log.info("Initialized AI Provider: {} with priority {} (Model: {})", 
                         provider.getName(), provider.getPriority(), provider.getModel());
            } catch (Exception e) {
                log.error("Failed to initialize AI Provider: {}", provider.getName(), e);
            }
        }
    }

    public String chat(String message) {
        return execute("general_chat", client -> client.prompt(message).call().content());
    }

    public <T> T execute(String sceneCode, Function<ChatClient, T> action) {
        if (!aiProperties.isEnable() || activeProviders == null || activeProviders.isEmpty()) {
            throw new BusinessException(503, "AI 服务未启用或未配置可用模型");
        }

        int maxRetries = aiProperties.getFailover() != null ? aiProperties.getFailover().getRetryCount() : 3;
        int threshold = aiProperties.getFailover() != null ? aiProperties.getFailover().getFailureThreshold() : 5;

        for (AiProperties.Provider provider : activeProviders) {
            String providerName = provider.getName();
            
            // Circuit Breaker check
            if (failureCounts.getOrDefault(providerName, 0) >= threshold) {
                log.warn("Provider {} is down (exceeded failure threshold), falling back to next...", providerName);
                continue;
            }

            ChatClient client = chatClients.get(providerName);
            if (client == null) {
                continue;
            }

            int attempts = 0;
            while (attempts < maxRetries) {
                try {
                    log.info("Requesting AI generation from provider: {}, scene={}", providerName, sceneCode);
                    T response = action.apply(client);
                    
                    // Reset failure count on success
                    failureCounts.put(providerName, 0);
                    return response;
                    
                } catch (Exception e) {
                    attempts++;
                    log.error("Provider {} failed (attempt {}/{}): {}", providerName, attempts, maxRetries, e.getMessage());
                    
                    if (attempts >= maxRetries) {
                        failureCounts.put(providerName, failureCounts.getOrDefault(providerName, 0) + 1);
                        break; // Exhausted retries, fail over to the next provider
                    }
                    
                    try {
                        long sleepTime = aiProperties.getFailover() != null ? aiProperties.getFailover().getRetryInterval() : 1000;
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException(503, "AI 服务调用在重试阶段被中断");
                    }
                }
            }
        }

        throw new BusinessException(503, "所有 AI 提供商均调用失败，请稍后重试");
    }
}
