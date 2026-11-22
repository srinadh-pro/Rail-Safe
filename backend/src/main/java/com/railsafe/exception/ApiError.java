package com.railsafe.exception;

import java.time.LocalDateTime;
import java.util.Map;

/** Standard JSON error body used by every REST endpoint. */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(LocalDateTime.now(), status, error, message, path, null);
    }

    public static ApiError validation(String message, String path, Map<String, String> fieldErrors) {
        return new ApiError(LocalDateTime.now(), 400, "Bad Request", message, path, fieldErrors);
    }
}
