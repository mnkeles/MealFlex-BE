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
    @Column(name = "email_status", nullable = false, length = 20)
    @Builder.Default private String emailStatus = "PENDING";
    @Column(name = "email_attempts", nullable = false)
    @Builder.Default private int emailAttempts = 0;
    @Column(name = "next_email_attempt_at")
    private Instant nextEmailAttemptAt;
    @Column(name = "email_sent_at")
    private Instant emailSentAt;
    @Column(name = "last_email_error", length = 500)
    private String lastEmailError;
}

