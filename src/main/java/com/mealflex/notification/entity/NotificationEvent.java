package com.mealflex.notification.entity;
import com.mealflex.common.entity.BaseEntity; import com.mealflex.user.entity.User; import jakarta.persistence.*; import lombok.*; import java.time.Instant;
@Entity
@Table(name = "notification_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEvent extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    private String eventType;
    private String channels;
    private String deliveredChannels;
    private String title;
    @Column(columnDefinition = "TEXT") private String body;
    private String referenceType;
    private Long referenceId;
    @Builder.Default private String status = "PENDING";
    @Builder.Default private int attempts = 0;
    private Instant nextAttemptAt;
    @Column(columnDefinition = "TEXT") private String lastError;
}
