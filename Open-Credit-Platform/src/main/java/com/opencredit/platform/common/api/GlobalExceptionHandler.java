package com.opencredit.platform.common.api;

import com.opencredit.platform.loan.ProductType;
import com.opencredit.platform.loan.exception.LoanApplicationNotFoundException;
import com.opencredit.platform.loan.exception.UnsupportedProductTypeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.InvalidTypeIdException;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Ensures the {@link ApiResponse} envelope holds on every error path, not just on success.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex,
                                                                HttpServletRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> Objects.requireNonNullElse(fe.getDefaultMessage(), "invalid"),
                        (a, b) -> a
                ));

        ApiError error = ApiError.of(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "VALIDATION_ERROR",
                "Request validation failed",
                request.getRequestURI()
        ).withFieldErrors(fieldErrors);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ApiResponse.failure(error));
    }

    /**
     * An unrecognized {@code productType} never reaches {@code LoanServiceContext}: Jackson
     * fails during deserialization with {@link InvalidTypeIdException}, which Spring wraps
     * here. We unwrap the cause to tell that apart from ordinary malformed JSON.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex,
                                                                HttpServletRequest request) {
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidTypeIdException typeIdEx) {
            String supported = Arrays.stream(ProductType.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            ApiError error = ApiError.of(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_PRODUCT_TYPE",
                    "Unsupported productType '" + typeIdEx.getTypeId() + "'. Supported values: " + supported,
                    request.getRequestURI()
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failure(error));
        }

        ApiError error = ApiError.of(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "Request body is malformed or missing required fields",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(UnsupportedProductTypeException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedProductType(UnsupportedProductTypeException ex,
                                                                            HttpServletRequest request) {
        log.warn("Product type recognized but no strategy bean registered: {}", ex.getProductType());
        ApiError error = ApiError.of(
                HttpStatus.BAD_REQUEST,
                "UNSUPPORTED_PRODUCT_TYPE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(LoanApplicationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(LoanApplicationNotFoundException ex,
                                                               HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "LOAN_APPLICATION_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception processing request {}", request.getRequestURI(), ex);
        ApiError error = ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure(error));
    }
}
