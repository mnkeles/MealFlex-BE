package com.mealflex.security;

import com.mealflex.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RecentAuthenticationService {
    private final ConcurrentHashMap<String, Instant> tokens = new ConcurrentHashMap<>();
    public String issue(Long userId) { String token = UUID.randomUUID().toString(); tokens.put(userId + ":" + token, Instant.now().plusSeconds(10 * 60)); return token; }
    public void require(Long userId, String token) {
        Instant expires = token == null ? null : tokens.remove(userId + ":" + token);
        if (expires == null || expires.isBefore(Instant.now())) throw new BusinessException("REAUTH_REQUIRED", "Bu işlem için şifrenizi yeniden doğrulamalısınız.");
    }
}
