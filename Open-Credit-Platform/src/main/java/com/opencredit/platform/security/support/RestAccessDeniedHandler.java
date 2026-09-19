package com.opencredit.platform.security.support;

import com.opencredit.platform.common.api.ApiError;
import com.opencredit.platform.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Same envelope as {@link RestAuthenticationEntryPoint}, for the "authenticated but wrong role"
 * case — an authenticated {@code MAKER} calling a {@code checker-decision} endpoint, or a
 * non-{@code ADMIN} calling an admin-config endpoint.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        ApiError error = ApiError.of(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have the required role for this operation", request.getRequestURI());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(error)));
    }
}
