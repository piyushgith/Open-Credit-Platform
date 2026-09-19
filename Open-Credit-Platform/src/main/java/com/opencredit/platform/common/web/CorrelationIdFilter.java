package com.opencredit.platform.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Reads (or generates) {@code X-Request-Id} per request, and {@code X-Correlation-Id} (defaulted
 * to the request id when the caller doesn't supply one — e.g. a multi-step demo script that wants
 * several calls tied together). Both go into {@link RequestContext} for {@code AuditService} and
 * into MDC so the logging pattern can print them. Registered ahead of the JWT filter (see
 * {@code SecurityConfig}) so every audit row and log line carries an id, authenticated or not.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /** Matches the {@code VARCHAR(60)} width of {@code audit_business_event}/{@code audit_data_change}'s id columns. */
    private static final int MAX_ID_LENGTH = 60;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = StringUtils.hasText(request.getHeader(REQUEST_ID_HEADER))
                ? truncate(request.getHeader(REQUEST_ID_HEADER))
                : UUID.randomUUID().toString();
        String correlationId = StringUtils.hasText(request.getHeader(CORRELATION_ID_HEADER))
                ? truncate(request.getHeader(CORRELATION_ID_HEADER))
                : requestId;

        RequestContext.set(requestId, correlationId);
        MDC.put("requestId", requestId);
        MDC.put("correlationId", correlationId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            RequestContext.clear();
            MDC.remove("requestId");
            MDC.remove("correlationId");
        }
    }

    private static String truncate(String value) {
        return value.length() > MAX_ID_LENGTH ? value.substring(0, MAX_ID_LENGTH) : value;
    }
}
