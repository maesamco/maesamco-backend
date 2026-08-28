package com.maesamco.judge.global.response;

/**
 * 성공 응답 공통 포맷.
 * { "success": true, "data": {...} }
 */
public record SuccessResponse<T>(boolean success, T data) {

    public static <T> SuccessResponse<T> success(T data) {
        return new SuccessResponse<>(true, data);
    }

    public static SuccessResponse<Void> empty() {
        return new SuccessResponse<>(true, null);
    }
}
