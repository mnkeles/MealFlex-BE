package com.mealflex.security;

import com.mealflex.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Small in-memory guard. Production deployments should additionally use their gateway/WAF. */
@Service
public class LoginRateLimitService {
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 15 * 60;
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    public void check(String email, String ip) {
        Attempt attempt = attempts.get(key(email, ip));
        if (attempt != null && attempt.count >= MAX_ATTEMPTS && attempt.lastAttempt.plusSeconds(WINDOW_SECONDS).isAfter(Instant.now()))
            throw new BusinessException("LOGIN_RATE_LIMITED", "Çok fazla başarısız giriş denemesi. Lütfen daha sonra tekrar deneyin.", HttpStatus.TOO_MANY_REQUESTS);
    }
    public void fail(String email, String ip) { attempts.compute(key(email, ip), (ignored, previous) -> previous == null || previous.lastAttempt.plusSeconds(WINDOW_SECONDS).isBefore(Instant.now()) ? new Attempt(1, Instant.now()) : new Attempt(previous.count + 1, Instant.now())); }
    public void success(String email, String ip) { attempts.remove(key(email, ip)); }
    private String key(String email, String ip) { return (email == null ? "" : email.trim().toLowerCase()) + "|" + (ip == null ? "" : ip); }
    private record Attempt(int count, Instant lastAttempt) {}
}
