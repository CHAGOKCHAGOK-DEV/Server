package com.chagokchagok.server.global.common.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiResponse<T>(
    @JsonProperty("isSuccess") boolean isSuccess,
    String code,
    String message,
    T data
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
            true,
            "OK",
            "요청에 성공했습니다.",
            data
        );
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(
            true,
            "OK",
            message,
            data
        );
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(
            true,
            "OK",
            "요청에 성공했습니다.",
            null
        );
    }

    public static ApiResponse<Void> failure(
        String code,
        String message
    ) {
        return new ApiResponse<>(
            false,
            code,
            message,
            null
        );
    }

    public static <T> ApiResponse<T> failure(
        String code,
        String message,
        T data
    ) {
        return new ApiResponse<>(
            false,
            code,
            message,
            data
        );
    }
}
