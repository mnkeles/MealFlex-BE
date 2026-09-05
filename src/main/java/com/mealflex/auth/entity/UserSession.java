package com.mealflex.auth.entity;
import com.mealflex.common.entity.BaseEntity;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="user_sessions") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserSession extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
 @Column(name="refresh_token_hash",nullable=false,unique=true,length=64) private String refreshTokenHash;
 private String deviceName; private String ipAddress;
 @Column(nullable=false) private Instant lastSeenAt;
 @Column(nullable=false) private Instant expiresAt;
 private Instant revokedAt;
}
