package com.mealflex.seller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SellerProfileRequest {

    @NotBlank(message = "Firma unvanı zorunludur.")
    private String companyTitle;

    @NotBlank(message = "Vergi numarası zorunludur.")
    private String taxNumber;

    @NotBlank(message = "Vergi dairesi zorunludur.")
    private String taxOffice;

    @NotBlank(message = "Yetkili kişi zorunludur.")
    private String authorizedPerson;

    private String phone;
    private String bankName;
    private String iban;
}
