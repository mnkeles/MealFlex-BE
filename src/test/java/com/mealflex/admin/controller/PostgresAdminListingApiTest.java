package com.mealflex.admin.controller;

import com.mealflex.security.UserPrincipal;
import com.mealflex.user.entity.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.task.scheduling.enabled=false",
        "logging.level.org.hibernate.SQL=WARN"
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "MEALFLEX_QA_DB_URL",
        matches = "jdbc:postgresql://localhost:5432/mealflex_qa_[0-9_]+")
class PostgresAdminListingApiTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("MEALFLEX_QA_DB_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("MEALFLEX_QA_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("MEALFLEX_QA_DB_PASSWORD"));
    }

    @Autowired MockMvc mockMvc;

    @Test
    void adminListingsAcceptOmittedOptionalTextFiltersOnPostgres() throws Exception {
        mockMvc.perform(get("/v1/admin/users").param("size", "1").with(asAdmin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/admin/stores").param("size", "1").with(asAdmin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/admin/audit-logs").param("size", "1").with(asAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void adminListingsApplyCombinedOptionalFiltersOnPostgres() throws Exception {
        mockMvc.perform(get("/v1/admin/users")
                        .param("role", "customer").param("search", "qa").with(asAdmin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/admin/stores")
                        .param("status", "active").param("search", "mutfak").with(asAdmin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/admin/audit-logs")
                        .param("actorId", "1")
                        .param("entityType", "payment")
                        .param("action", "refund")
                        .param("startDate", "2020-01-01")
                        .param("endDate", "2030-01-01")
                        .with(asAdmin()))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor asAdmin() {
        UserPrincipal principal = new UserPrincipal(1L, "qa-admin@mealflex.test", "qa-only",
                Role.ADMIN, true, List.of(() -> "ROLE_ADMIN"));
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities()));
    }
}
