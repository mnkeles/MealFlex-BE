package com.mealflex.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {

    @NotBlank(message = "Ad zorunludur.")
    private String firstName;

    @NotBlank(message = "Soyad zorunludur.")
    private String lastName;

    private String phone;

    private String companyName;
    private String taxNumber;
    private String taxOffice;
    private String invoiceAddress;
}
