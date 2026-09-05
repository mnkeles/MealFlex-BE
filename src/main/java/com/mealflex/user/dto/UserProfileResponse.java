package com.mealflex.user.dto;

import com.mealflex.user.entity.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserProfileResponse {

    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private Role role;
    private boolean emailVerified;
    private boolean phoneVerified;

    private String companyName;
    private String taxNumber;
    private String taxOffice;
    private String invoiceAddress;
}
