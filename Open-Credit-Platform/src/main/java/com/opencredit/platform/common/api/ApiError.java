package com.opencredit.platform.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized error detail carried inside {@code ApiResponse.errors} on the failure path.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {

    public static ApiError of(HttpStatus httpStatus, String code, String message, String path) {
        return new ApiError(Instant.now(), httpStatus.value(), httpStatus.name(), code, message, path, null);
    }

    public ApiError withFieldErrors(Map<String, String> fieldErrors) {
        return new ApiError(timestamp, status, error, code, message, path, fieldErrors);
    }
}
