package com.aimv.shared.api;

import com.aimv.shared.error.ApiError;

public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
