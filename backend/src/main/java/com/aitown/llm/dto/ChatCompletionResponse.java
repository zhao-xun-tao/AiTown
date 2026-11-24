package com.aitown.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionResponse {

    private List<Choice> choices;

    private UsageInfo usage;

    public String firstContent() {
        if (choices == null || choices.isEmpty() || choices.get(0).getMessage() == null) {
            return null;
        }
        return choices.get(0).getMessage().getContent();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private ChatMessage message;
    }
}
