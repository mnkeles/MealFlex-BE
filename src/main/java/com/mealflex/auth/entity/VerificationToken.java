package com.mealflex.auth.entity;
import com.mealflex.common.entity.BaseEntity;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="verification_tokens") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VerificationToken extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
 @Column(nullable=false) private String tokenType;
 @Column(nullable=false,unique=true,length=64) private String tokenHash;
 @Column(nullable=false) private Instant expiresAt;
 private Instant usedAt;
 @Column(nullable=false) @Builder.Default private Integer attempts=0;
}
