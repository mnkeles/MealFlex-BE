package com.mealflex.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** IP-scoped guard for password reset, OTP, payment and upload endpoints. */
public class SensitiveEndpointRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_REQUESTS = 12;
    private static final long WINDOW_SECONDS = 10 * 60;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SensitiveEndpointRateLimitFilter(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.contains("/v1/auth/forgot-password") || path.contains("/v1/auth/reset-password")
                || path.contains("/v1/account/phone/") || path.contains("/v1/account/email/")
                || path.contains("/v1/payment-methods") || path.contains("/v1/payments/")
                || path.contains("/attachments") || path.contains("/documents"));
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws java.io.IOException, jakarta.servlet.ServletException {
        String key = request.getRequestURI() + "|" + request.getRemoteAddr();
        Instant now = Instant.now();
        Window next = windows.compute(key, (ignored, current) -> current == null || current.startedAt.plusSeconds(WINDOW_SECONDS).isBefore(now) ? new Window(1, now) : new Window(current.count + 1, current.startedAt));
        if (next.count > MAX_REQUESTS) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            objectMapper.writeValue(response.getOutputStream(), Map.of("code", "SENSITIVE_ENDPOINT_RATE_LIMITED", "message", "Bu işlem için çok fazla istek gönderildi. Lütfen daha sonra tekrar deneyin."));
            return;
        }
        chain.doFilter(request, response);
    }

    private record Window(int count, Instant startedAt) {}
}
