package com.opencredit.platform.common.web;

/**
 * Per-request request id / correlation id, populated by {@link CorrelationIdFilter} and read by
 * {@code AuditService} and anything else that needs to tag a record with "which request did this."
 * Backed by a {@link ThreadLocal} rather than a request-scoped bean: business services in this
 * codebase are plain singletons with no dependency on {@code HttpServletRequest}, and a static
 * accessor keeps it that way. Always cleared by the filter in a {@code finally} block.
 */
public final class RequestContext {

    private static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();

    private final String requestId;
    private final String correlationId;

    private RequestContext(String requestId, String correlationId) {
        this.requestId = requestId;
        this.correlationId = correlationId;
    }

    static void set(String requestId, String correlationId) {
        CURRENT.set(new RequestContext(requestId, correlationId));
    }

    static void clear() {
        CURRENT.remove();
    }

    /** {@code "unknown"} outside a request thread (e.g. a unit test calling a service directly). */
    public static String currentRequestId() {
        RequestContext context = CURRENT.get();
        return context != null ? context.requestId : "unknown";
    }

    public static String currentCorrelationId() {
        RequestContext context = CURRENT.get();
        return context != null ? context.correlationId : "unknown";
    }
}
