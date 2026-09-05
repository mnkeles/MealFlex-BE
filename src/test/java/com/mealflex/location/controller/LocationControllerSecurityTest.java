package com.mealflex.location.controller;

import com.mealflex.location.dto.LocationOptionResponse;
import com.mealflex.location.service.LocationService;
import com.mealflex.security.JwtAuthenticationFilter;
import com.mealflex.security.SecurityConfig;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class LocationControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private LocationService locationService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private JpaMetamodelMappingContext jpaMappingContext;

    @BeforeEach
    void continueThroughJwtFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void provincesArePublicWithoutAuthentication() throws Exception {
        when(locationService.getProvinces()).thenReturn(List.of(new LocationOptionResponse(6L, "Ankara")));

        mockMvc.perform(get("/v1/locations/provinces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ankara"));
    }

    @Test
    void protectedSellerEndpointReturnsUnauthorizedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v1/seller/stores"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
