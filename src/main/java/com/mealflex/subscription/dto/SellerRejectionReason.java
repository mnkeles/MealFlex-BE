package com.mealflex.subscription.dto;

public enum SellerRejectionReason {
    CAPACITY_FULL("Seçilen dönem için kapasitemiz dolu."),
    SERVICE_DATES_UNAVAILABLE("Talep edilen tarihlerde hizmet veremiyoruz."),
    DELIVERY_TIME_UNAVAILABLE("Talep edilen teslimat saati uygun değil."),
    SERVICE_AREA_UNAVAILABLE("Talep edilen bölgeye hizmet veremiyoruz."),
    MENU_UNAVAILABLE("Seçilen menü şu anda sunulamıyor."),
    MINIMUM_ORDER_NOT_MET("Talep, minimum sipariş koşullarını karşılamıyor."),
    TEMPORARY_OPERATIONAL_CLOSURE("İşletmemiz talep edilen dönemde geçici olarak hizmet veremiyor."),
    STAFFING_CONSTRAINT("Talep edilen dönem için operasyon ekibimiz yeterli değil."),
    SUPPLY_CONSTRAINT("Talep edilen hizmet için gerekli ürün tedariği sağlanamıyor."),
    REQUEST_DETAILS_UNSUITABLE("Talep detayları mevcut hizmet koşullarımıza uygun değil.");

    private final String customerMessage;

    SellerRejectionReason(String customerMessage) {
        this.customerMessage = customerMessage;
    }

    public String customerMessage() {
        return customerMessage;
    }
}
