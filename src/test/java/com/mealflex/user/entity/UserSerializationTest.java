package com.mealflex.user.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserSerializationTest {

    @Test
    void passwordHashIsNeverSerializedToApiResponses() throws Exception {
        User user = User.builder()
                .email("admin@example.com")
                .password("encoded-password-hash")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ADMIN)
                .build();

        String json = new ObjectMapper().writeValueAsString(user);

        assertThat(json)
                .doesNotContain("password")
                .doesNotContain("encoded-password-hash");
    }
}
