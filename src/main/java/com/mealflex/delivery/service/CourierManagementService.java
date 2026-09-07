package com.mealflex.delivery.service;

import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.delivery.entity.Courier;
import com.mealflex.delivery.repository.CourierRepository;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.store.service.SellerStoreAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CourierManagementService {
    private final CourierRepository courierRepository;
    private final SubscriptionDeliveryRepository deliveryRepository;
    private final SellerStoreAccessService storeAccess;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long sellerId, Long storeId) {
        storeAccess.requireOwnedStore(sellerId, storeId);
        return courierRepository.findByStoreIdAndDeletedAtIsNull(storeId).stream().map(courier -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", courier.getId());
            result.put("fullName", courier.getFullName());
            result.put("phone", mask(courier.getPhone()));
            result.put("active", courier.isActive());
            result.put("workspaceLinked", courier.getEmail() != null && !courier.getEmail().isBlank());
            return result;
        }).toList();
    }

    @Transactional
    public Map<String, Object> create(Long sellerId, Long storeId, Map<String, String> body) {
        var store = storeAccess.requireOwnedStore(sellerId, storeId);
        String email = body.get("email") == null ? null : body.get("email").trim().toLowerCase();
        Courier courier = courierRepository.save(Courier.builder().store(store)
                .fullName(body.get("fullName")).phone(body.get("phone"))
                .email(email == null || email.isBlank() ? null : email).build());
        return Map.of("id", courier.getId(), "fullName", courier.getFullName());
    }

    @Transactional
    public void update(Long sellerId, Long storeId, Long courierId, Map<String, Object> body) {
        storeAccess.requireOwnedStore(sellerId, storeId);
        Courier courier = ownedCourier(courierId, storeId);
        if (body.get("active") != null) courier.setActive(Boolean.TRUE.equals(body.get("active")));
        if (body.get("fullName") != null) courier.setFullName(String.valueOf(body.get("fullName")));
        courierRepository.save(courier);
    }

    @Transactional
    public void assign(Long sellerId, Long storeId, Long deliveryId, Map<String, Object> body) {
        storeAccess.requireOwnedStore(sellerId, storeId);
        var delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Teslimat", deliveryId));
        if (!delivery.getSubscription().getStore().getId().equals(storeId)) {
            throw new ResourceNotFoundException("Teslimat", deliveryId);
        }
        if (body.get("courierId") != null) {
            delivery.setCourier(ownedCourier(((Number) body.get("courierId")).longValue(), storeId));
        }
        if (body.get("routeSequence") != null) delivery.setRouteSequence(((Number) body.get("routeSequence")).intValue());
        if (body.get("deliveryType") != null) delivery.setDeliveryType(String.valueOf(body.get("deliveryType")));
        if (body.get("deliveryDate") != null) delivery.setDeliveryDate(LocalDate.parse(String.valueOf(body.get("deliveryDate"))));
        if (body.get("deliveryTime") != null) delivery.setDeliveryTime(LocalTime.parse(String.valueOf(body.get("deliveryTime"))));
        deliveryRepository.save(delivery);
    }

    private Courier ownedCourier(Long courierId, Long storeId) {
        return courierRepository.findByIdAndStoreIdAndDeletedAtIsNull(courierId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Kurye", courierId));
    }

    private String mask(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "*** *** " + phone.substring(phone.length() - 4);
    }
}
