package com.mealflex.seller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Set;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StoreStaffResponse {
    private Long id;
    private String email;
    private String fullName;
    private String role;
    private String status;
    private Set<String> permissions;
    private Instant invitationExpiresAt;
    private Instant acceptedAt;
    /** Yalnız davet oluşturma yanıtında bir kez döner; listelerde ve veritabanında ham olarak tutulmaz. */
    private String invitationToken;
}
