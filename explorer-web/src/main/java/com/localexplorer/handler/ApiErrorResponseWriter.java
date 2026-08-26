package com.localexplorer.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.constant.ErrorCode;
import com.localexplorer.filter.RequestTracingFilter;
import com.localexplorer.result.Result;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    @Autowired
    public ApiErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        String requestId = MDC.get(RequestTracingFilter.MDC_KEY);
        response.setStatus(errorCode.getHttpStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (requestId != null) {
            response.setHeader(RequestTracingFilter.REQUEST_ID_HEADER, requestId);
        }
        Result<Void> result = Result.error(errorCode, message);
        result.setRequestId(requestId);
        objectMapper.writeValue(response.getWriter(), result);
    }
}
