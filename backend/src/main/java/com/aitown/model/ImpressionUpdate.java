package com.aitown.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 决策中附带的对其他居民的印象/好感度更新。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImpressionUpdate {

    /** 对方名字 */
    private String target;

    /** 好感度变化，建议 -5 ~ +5 */
    private Integer delta;

    /** 印象一句话 */
    private String note;
}
