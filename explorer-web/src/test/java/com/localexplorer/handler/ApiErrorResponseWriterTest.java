package com.localexplorer.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.constant.ErrorCode;
import com.localexplorer.filter.RequestTracingFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorResponseWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void authenticationErrorCarriesRequestIdInHeaderAndBody() throws Exception {
        MDC.put(RequestTracingFilter.MDC_KEY, "trace-auth-01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiErrorResponseWriter(objectMapper)
                .write(response, ErrorCode.AUTHENTICATION_FAILED, "登录状态无效");

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getHeader(RequestTracingFilter.REQUEST_ID_HEADER)).isEqualTo("trace-auth-01");
        assertThat(body.path("requestId").asText()).isEqualTo("trace-auth-01");
    }
}
