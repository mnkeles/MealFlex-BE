package com.mealflex.seller.entity;
import com.mealflex.common.entity.BaseEntity;
import com.mealflex.store.entity.Store;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="store_staff") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StoreStaff extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="store_id",nullable=false) private Store store;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id") private User user;
 @Column(nullable=false) private String email;
 @Column(name="staff_role",nullable=false) private String staffRole;
 @Column(nullable=false) @Builder.Default private String status="INVITED";
 private String invitationTokenHash; private Instant invitationExpiresAt; private Instant acceptedAt; private Instant deactivatedAt;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="invited_by",nullable=false) private User invitedBy;
}
