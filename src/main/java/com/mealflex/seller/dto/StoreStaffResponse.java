package com.mealflex.seller.dto;
import lombok.*; import java.time.Instant; import java.util.Set;
@Getter @Builder @AllArgsConstructor public class StoreStaffResponse { private Long id; private String email; private String fullName; private String role; private String status; private Set<String> permissions; private Instant invitationExpiresAt; private Instant acceptedAt; }
