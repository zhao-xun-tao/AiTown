package com.aitown.llm;

import com.aitown.config.LlmProperties;
import com.aitown.llm.dto.ChatCompletionRequest;
import com.aitown.llm.dto.ChatCompletionResponse;
import com.aitown.llm.dto.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * OpenAI 兼容 Chat Completions HTTP 客户端。
 * 支持任意提供 base-url + api-key + model 的兼容网关。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 发送一轮对话，返回 assistant 文本内容。
     * 超时或 HTTP 错误抛出 {@link LlmException}，由上层降级为 IDLE。
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new LlmException("llm.api-key 未配置");
        }

        String url = normalizeBaseUrl(properties.getBaseUrl()) + "/chat/completions";

        ChatCompletionRequest body = ChatCompletionRequest.builder()
                .model(properties.getModel())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .messages(List.of(
                        ChatMessage.builder().role("system").content(systemPrompt).build(),
                        ChatMessage.builder().role("user").content(userPrompt).build()
                ))
                .build();

        try {
            String json = objectMapper.writeValueAsString(body);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            log.debug("调用 LLM: model={}, url={}", properties.getModel(), url);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmException("LLM HTTP " + response.statusCode() + ": " + truncate(response.body()));
            }

            ChatCompletionResponse parsed = objectMapper.readValue(response.body(), ChatCompletionResponse.class);
            String content = parsed.firstContent();
            if (content == null || content.isBlank()) {
                throw new LlmException("LLM 返回空 content");
            }
            return content;
        } catch (LlmException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new LlmException("LLM 调用超时(" + properties.getTimeoutMs() + "ms)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM 调用被中断", e);
        } catch (Exception e) {
            throw new LlmException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.com/v1";
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
