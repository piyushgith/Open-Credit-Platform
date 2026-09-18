package com.opencredit.platform.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Generic envelope wrapping every API response, success or failure, in one predictable shape.
 * {@code data} is present only on success, {@code errors} only on failure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private String status;       // "SUCCESS" or "ERROR"
    private String message;      // Human-readable message
    private Instant timestamp;
    private T data;              // The generic payload (e.g. LoanResponse)
    private List<ApiError> errors;

    // --- Static Factory Methods for clean Controller code ---

    public static <T> ApiResponse<T> success(T data) {
        return success("Operation completed successfully", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .status("SUCCESS")
                .message(message)
                .timestamp(Instant.now())
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> failure(ApiError error) {
        return failure(error.message(), Collections.singletonList(error));
    }

    public static <T> ApiResponse<T> failure(String message, List<ApiError> errors) {
        return ApiResponse.<T>builder()
                .status("ERROR")
                .message(message)
                .timestamp(Instant.now())
                .errors(errors)
                .build();
    }
}
