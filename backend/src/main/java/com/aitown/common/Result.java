package com.aitown.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应结构。
 * 前后端约定：所有 REST 接口均返回 Result&lt;T&gt;。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 业务状态码：0 成功，非 0 失败 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(0, message, data);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(-1, message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
