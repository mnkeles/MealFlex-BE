package com.mealflex.admin.dto;

import com.mealflex.user.entity.User;

import java.time.Instant;

public record AdminUserResponse(
        Long id, String email, String firstName, String lastName, String phone,
        String role, boolean active, boolean emailVerified, Instant createdAt,
        Instant accountDeletedAt) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhone(), user.getRole().name(), user.isActive(), user.isEmailVerified(),
                user.getCreatedAt(), user.getAccountDeletedAt());
    }
}
