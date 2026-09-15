package com.mealflex.support.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "support_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SupportRequest extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "account_role", nullable = false, length = 30)
    private String accountRole;
    @Column(name = "contact_name", nullable = false, length = 120)
    private String contactName;
    @Column(name = "contact_email", nullable = false)
    private String contactEmail;
    @Column(name = "contact_phone", length = 25)
    private String contactPhone;
    @Column(nullable = false, length = 30)
    private String category;
    @Column(nullable = false, length = 150)
    private String subject;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;
    @Column(nullable = false, length = 20)
    @Builder.Default private String status = "NEW";
    @Column(name = "admin_response", columnDefinition = "TEXT")
    private String adminResponse;
    @Column(name = "responded_at")
    private Instant respondedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by_user_id")
    private User respondedBy;
}
