package com.aimv.shared.error;

public record ApiError(
        String code,
        String message
) {
}
