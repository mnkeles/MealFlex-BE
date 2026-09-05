package com.mealflex.user.entity;
import com.mealflex.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="notification_preferences") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationPreference extends BaseEntity {
 @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false,unique=true) private User user;
 @Column(nullable=false) @Builder.Default private boolean emailEnabled=true;
 @Column(nullable=false) @Builder.Default private boolean smsEnabled=true;
 @Column(nullable=false) @Builder.Default private boolean pushEnabled=true;
 @Column(nullable=false) @Builder.Default private boolean marketingEnabled=false;
}
