package com.mealflex.auth.repository;
import com.mealflex.auth.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
public interface UserSessionRepository extends JpaRepository<UserSession,Long> {
 Optional<UserSession> findByRefreshTokenHash(String hash);
 List<UserSession> findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(Long userId,Instant now);
 Optional<UserSession> findByIdAndUserId(Long id,Long userId);
}
