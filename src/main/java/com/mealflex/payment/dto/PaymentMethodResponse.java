package com.mealflex.payment.dto;
public record PaymentMethodResponse(Long id, String brand, String lastFour, Integer expiryMonth, Integer expiryYear, boolean defaultMethod) {}
