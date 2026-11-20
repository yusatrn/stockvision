package com.stockvision.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void preservesValidIncomingTraceIdAndCleansMdc() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(TraceIdFilter.HEADER_NAME, "client_trace-01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain =
                (req, res) -> {
                    assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isEqualTo("client_trace-01");
                    assertThat(req.getAttribute(TraceIdFilter.REQUEST_ATTRIBUTE))
                            .isEqualTo("client_trace-01");
                };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(TraceIdFilter.HEADER_NAME)).isEqualTo("client_trace-01");
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesUuidWhenIncomingHeaderIsMissingOrInvalid() throws ServletException, IOException {
        for (String invalidHeader :
                new String[] {null, "", "   ", "invalid space", "bad/slash", "x".repeat(65)}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
            if (invalidHeader != null) {
                request.addHeader(TraceIdFilter.HEADER_NAME, invalidHeader);
            }
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> {});

            String assigned = response.getHeader(TraceIdFilter.HEADER_NAME);
            assertThat(assigned).isNotBlank();
            assertThat(UUID.fromString(assigned)).isNotNull();
            assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
        }
    }

    @Test
    void clearsMdcEvenWhenFilterChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain failingChain =
                (req, res) -> {
                    throw new IllegalStateException("downstream failure");
                };

        assertThatThrownBy(() -> filter.doFilter(request, response, failingChain))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }
}
