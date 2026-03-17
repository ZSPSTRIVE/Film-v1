package com.jelly.cinema.service;

import com.jelly.cinema.common.config.property.AiProperties;
import com.jelly.cinema.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
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
                if (!isProviderConfigured(provider)) {
                    log.warn("Skip AI Provider {} because apiKey/baseUrl/model is missing.", provider.getName());
                    continue;
                }

                OpenAiApi openAiApi = new OpenAiApi(provider.getBaseUrl(), provider.getApiKey());
                OpenAiChatOptions options = OpenAiChatOptions.builder()
                        .model(provider.getModel())
                        .temperature(provider.getTemperature())
                        .maxTokens(provider.getMaxTokens())
                        .build();

                OpenAiChatModel chatModel = new OpenAiChatModel(
                        openAiApi,
                        options,
                        null,
                        RetryTemplate.builder()
                                .maxAttempts(1)
                                .retryOn(Exception.class)
                                .build()
                );
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

        int maxRetries = aiProperties.getFailover() != null
                ? Math.max(1, aiProperties.getFailover().getRetryCount())
                : 3;
        int threshold = aiProperties.getFailover() != null
                ? Math.max(1, aiProperties.getFailover().getFailureThreshold())
                : 5;
        Map<String, String> providerErrors = new LinkedHashMap<>();

        for (AiProperties.Provider provider : activeProviders) {
            String providerName = provider.getName();
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
                    failureCounts.put(providerName, 0);
                    return response;
                } catch (Exception e) {
                    attempts++;
                    String brief = sanitizeErrorMessage(e.getMessage());
                    providerErrors.put(providerName, brief);
                    log.error("Provider {} failed (attempt {}/{}): {}", providerName, attempts, maxRetries, brief);

                    if (isLikelyProviderConfigError(e)) {
                        failureCounts.put(providerName, threshold);
                        break;
                    }

                    if (attempts >= maxRetries) {
                        failureCounts.put(providerName, failureCounts.getOrDefault(providerName, 0) + 1);
                        break;
                    }

                    try {
                        long sleepTime = aiProperties.getFailover() != null
                                ? aiProperties.getFailover().getRetryInterval()
                                : 1000;
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException(503, "AI 服务调用在重试阶段被中断");
                    }
                }
            }
        }

        if (!providerErrors.isEmpty()) {
            String details = providerErrors.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("; "));
            throw new BusinessException(503, "所有 AI 提供商均调用失败，请检查 AI 配置与密钥。详情: " + details);
        }
        throw new BusinessException(503, "所有 AI 提供商均调用失败，请稍后重试");
    }

    private boolean isProviderConfigured(AiProperties.Provider provider) {
        return StringUtils.hasText(provider.getApiKey())
                && StringUtils.hasText(provider.getBaseUrl())
                && StringUtils.hasText(provider.getModel());
    }

    private boolean isLikelyProviderConfigError(Exception e) {
        String msg = e.getMessage();
        if (!StringUtils.hasText(msg)) {
            return false;
        }
        String lower = msg.toLowerCase();
        return lower.contains("401")
                || lower.contains("403")
                || lower.contains("404")
                || lower.contains("authentication")
                || lower.contains("unauthorized")
                || lower.contains("bearer sk-")
                || lower.contains("invalid api key")
                || lower.contains("not found")
                || lower.contains("model not found")
                || lower.contains("unknown model");
    }

    private String sanitizeErrorMessage(String msg) {
        if (!StringUtils.hasText(msg)) {
            return "unknown error";
        }
        String oneLine = msg.replace("\r", " ").replace("\n", " ").trim();
        return oneLine.length() > 220 ? oneLine.substring(0, 220) + "..." : oneLine;
    }
}
