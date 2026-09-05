package com.mealflex.subscription.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class CreateSubscriptionRequest {

    @NotNull(message = "Mağaza ID zorunludur.")
    private Long storeId;

    @NotNull(message = "Menü ID zorunludur.")
    private Long menuId;

    @NotNull(message = "Adres ID zorunludur.")
    private Long addressId;

    private Long paymentMethodId;

    private boolean commercialTermsAccepted;

    private String couponCode;

    @NotNull(message = "Kişi sayısı zorunludur.")
    @Min(value = 1, message = "Kişi sayısı en az 1 olmalıdır.")
    private Integer personCount;

    @NotNull(message = "Teslimat saati zorunludur.")
    private LocalTime deliveryTime;

    @NotNull(message = "Başlangıç tarihi zorunludur.")
    private LocalDate startDate;

    @NotNull(message = "Bitiş tarihi zorunludur.")
    private LocalDate endDate;
}
