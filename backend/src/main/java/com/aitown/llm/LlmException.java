package com.aitown.llm;

/**
 * LLM 调用失败（超时、鉴权、空响应等），上层应降级为 IDLE。
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
