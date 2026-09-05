package com.mealflex.payment.dto;
import jakarta.validation.constraints.*;
public record CreatePaymentMethodRequest(
    @NotBlank @Size(max=255) String providerToken,
    @Size(max=150) String cardHolderName,
    @NotBlank @Size(max=40) String brand,
    @NotBlank @Pattern(regexp="\\d{4}") String lastFour,
    @NotNull @Min(1) @Max(12) Integer expiryMonth,
    @NotNull @Min(2026) @Max(2100) Integer expiryYear,
    boolean makeDefault) {}
