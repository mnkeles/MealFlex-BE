package com.mealflex.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mealflex.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    @JsonIgnore
    private String password;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column
    private Instant emailVerifiedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean phoneVerified = false;

    @Column
    private Instant phoneVerifiedAt;

    @Column
    private Instant termsAcceptedAt;

    @Column
    private String termsVersion;

    @Column
    private Instant privacyAcceptedAt;

    @Column
    private String privacyVersion;

    @Column
    private Instant accountDeletedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(unique = true)
    private String referralCode;
}
