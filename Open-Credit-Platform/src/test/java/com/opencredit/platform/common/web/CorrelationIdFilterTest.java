package com.opencredit.platform.common.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level coverage for {@link CorrelationIdFilter}: no {@code @SpringBootTest} needed since the
 * filter only touches {@link RequestContext}, MDC and the request/response headers directly.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesARequestIdAndDefaultsCorrelationIdToItWhenNeitherHeaderIsSupplied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenRequestId = new String[1];
        String[] seenCorrelationId = new String[1];
        FilterChain chain = (req, res) -> {
            seenRequestId[0] = RequestContext.currentRequestId();
            seenCorrelationId[0] = RequestContext.currentCorrelationId();
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(seenRequestId[0]).isNotBlank();
        assertThat(seenCorrelationId[0]).isEqualTo(seenRequestId[0]);
        assertThat(response.getHeader(CorrelationIdFilter.REQUEST_ID_HEADER)).isEqualTo(seenRequestId[0]);
        assertThat(RequestContext.currentRequestId()).isEqualTo("unknown");
    }

    @Test
    void propagatesSuppliedRequestAndCorrelationIds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.REQUEST_ID_HEADER, "req-123");
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-456");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenRequestId = new String[1];
        String[] seenCorrelationId = new String[1];
        FilterChain chain = (req, res) -> {
            seenRequestId[0] = RequestContext.currentRequestId();
            seenCorrelationId[0] = RequestContext.currentCorrelationId();
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(seenRequestId[0]).isEqualTo("req-123");
        assertThat(seenCorrelationId[0]).isEqualTo("corr-456");
    }

    /**
     * {@code audit_business_event}/{@code audit_data_change} store both ids as {@code VARCHAR(60)};
     * an oversized client-supplied header must be truncated rather than reach {@code AuditService}
     * and fail the insert with a {@code DataIntegrityViolationException} that rolls back an
     * otherwise-valid business transaction.
     */
    @Test
    void truncatesOversizedSuppliedIdsToTheAuditColumnWidth() throws Exception {
        String oversized = "x".repeat(120);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.REQUEST_ID_HEADER, oversized);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenRequestId = new String[1];
        FilterChain chain = (req, res) -> seenRequestId[0] = RequestContext.currentRequestId();

        filter.doFilterInternal(request, response, chain);

        assertThat(seenRequestId[0]).hasSize(60).isEqualTo(oversized.substring(0, 60));
    }
}
