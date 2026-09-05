package com.mealflex.user.entity;
import com.mealflex.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="user_data_requests") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserDataRequest extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
 @Column(nullable=false) private String requestType;
 @Column(nullable=false) private String status;
 @Column(nullable=false) private Instant requestedAt;
 private Instant completedAt;
}
