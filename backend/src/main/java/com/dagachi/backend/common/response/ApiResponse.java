package com.dagachi.backend.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.ALWAYS) // data == null이어도 아예 필드를 없애지 않고 "data": null 을 유지
public class ApiResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;

    private ApiResponse(boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // 성공하면서 반환 데이터가 있는 경우
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                true,
                "SUCCESS",
                "요청이 성공했습니다.",
                data
        );
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(
                true,
                "SUCCESS",
                message,
                data
        );
    }

    // 성공하지만 반환할 데이터가 없는 경우
    public static ApiResponse<Void> success() {
        return new ApiResponse<>(
                true,
                "SUCCESS",
                "요청이 성공했습니다.",
                null
        );
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(
                true,
                "SUCCESS",
                message,
                null
        );
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(
                false,
                code,
                message,
                null
        );
    }
}