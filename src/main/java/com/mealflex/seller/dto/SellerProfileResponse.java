package com.mealflex.seller.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SellerProfileResponse {
    private Long id;
    private String companyTitle;
    private String taxNumber;
    private String taxOffice;
    private String authorizedPerson;
    private String phone;
    private String bankName;
    private String iban;
}
