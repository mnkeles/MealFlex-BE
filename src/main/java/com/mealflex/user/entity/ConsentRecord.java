package com.mealflex.user.entity;
import com.mealflex.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="consent_records") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConsentRecord extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
 @Column(nullable=false) private String documentType;
 @Column(nullable=false) private String documentVersion;
 @Column(nullable=false) private Instant acceptedAt;
 private String ipAddress;
}
