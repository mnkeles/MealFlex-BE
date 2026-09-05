package com.mealflex.auth.repository;
import com.mealflex.auth.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface VerificationTokenRepository extends JpaRepository<VerificationToken,Long> {
 Optional<VerificationToken> findByTokenHashAndTokenType(String tokenHash,String tokenType);
}
