package com.mealflex.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class SensitiveEndpointRateLimitFilterTest {

    @Test
    void passwordResetEndpointAllowsTwelveRequestsThenReturnsTooManyRequests() throws Exception {
        SensitiveEndpointRateLimitFilter filter = new SensitiveEndpointRateLimitFilter(new ObjectMapper());
        FilterChain chain = mock(FilterChain.class);

        for (int attempt = 1; attempt <= 13; attempt++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/forgot-password");
            request.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertEquals(attempt <= 12 ? 200 : 429, response.getStatus());
        }

        verify(chain, times(12)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void paymentAndDocumentEndpointsUseTheSameSensitiveRequestLimit() throws Exception {
        SensitiveEndpointRateLimitFilter filter = new SensitiveEndpointRateLimitFilter(new ObjectMapper());
        FilterChain chain = mock(FilterChain.class);
        for (String path : java.util.List.of("/api/v1/payments/preview", "/api/v1/seller/stores/5/documents")) {
            for (int attempt = 1; attempt <= 12; attempt++) {
                MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
                request.setRemoteAddr("192.168.1.13");
                MockHttpServletResponse response = new MockHttpServletResponse();
                filter.doFilter(request, response, chain);
                assertEquals(200, response.getStatus());
            }
            MockHttpServletRequest throttled = new MockHttpServletRequest("POST", path);
            throttled.setRemoteAddr("192.168.1.13");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(throttled, response, chain);
            assertEquals(429, response.getStatus());
        }
    }
}
