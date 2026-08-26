package com.localexplorer.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTracingFilterTest {

    private final RequestTracingFilter filter = new RequestTracingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void forwardsValidRequestIdThroughHeaderAndMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/explore-item/list");
        request.addHeader(RequestTracingFilter.REQUEST_ID_HEADER, "trace-20260824");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> requestIdInChain.set(MDC.get(RequestTracingFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(requestIdInChain.get()).isEqualTo("trace-20260824");
        assertThat(response.getHeader(RequestTracingFilter.REQUEST_ID_HEADER)).isEqualTo("trace-20260824");
        assertThat(MDC.get(RequestTracingFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesSafeRequestIdWhenHeaderIsMissingOrInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/explore-order");
        request.addHeader(RequestTracingFilter.REQUEST_ID_HEADER, "invalid request id with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInChain = new AtomicReference<>();

        filter.doFilter(request, response,
                (req, res) -> requestIdInChain.set(MDC.get(RequestTracingFilter.MDC_KEY)));

        assertThat(requestIdInChain.get()).matches("[a-f0-9]{32}");
        assertThat(response.getHeader(RequestTracingFilter.REQUEST_ID_HEADER)).isEqualTo(requestIdInChain.get());
        assertThat(MDC.get(RequestTracingFilter.MDC_KEY)).isNull();
    }
}
