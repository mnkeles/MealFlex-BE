package com.mealflex.subscription.entity;

/** Satıcının karar vermesini bekleyen teslimat düzenleme talebinin durumu. */
public enum DeliveryModificationRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** Eski, müşteri tarafından anında uygulanmış kayıtlar için geriye dönük durum. */
    APPLIED
}
