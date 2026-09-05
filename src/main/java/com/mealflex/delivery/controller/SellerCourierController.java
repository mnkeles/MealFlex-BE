package com.mealflex.delivery.controller;

import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.delivery.entity.Courier;
import com.mealflex.delivery.repository.CourierRepository;
import com.mealflex.delivery.repository.SubscriptionDeliveryRepository;
import com.mealflex.security.UserPrincipal;
import com.mealflex.store.service.SellerStoreAccessService;
import lombok.RequiredArgsConstructor; import org.springframework.http.*; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*;
import java.time.*; import java.util.*;

@RestController @RequestMapping("/v1/seller/stores/{storeId}/couriers") @RequiredArgsConstructor
public class SellerCourierController {
 private final CourierRepository courierRepository; private final SubscriptionDeliveryRepository deliveryRepository; private final SellerStoreAccessService access;
 @GetMapping public List<Map<String,Object>> list(@AuthenticationPrincipal UserPrincipal user,@PathVariable Long storeId){ access.requireOwnedStore(user.getId(),storeId); return courierRepository.findByStoreIdAndDeletedAtIsNull(storeId).stream().map(c->{Map<String,Object> result=new LinkedHashMap<>();result.put("id",c.getId());result.put("fullName",c.getFullName());result.put("phone",mask(c.getPhone()));result.put("active",c.isActive());result.put("workspaceLinked",c.getEmail()!=null&&!c.getEmail().isBlank());return result;}).toList(); }
 @PostMapping public ResponseEntity<Map<String,Object>> create(@AuthenticationPrincipal UserPrincipal user,@PathVariable Long storeId,@RequestBody Map<String,String> body){ var store=access.requireOwnedStore(user.getId(),storeId); String email=body.get("email")==null?null:body.get("email").trim().toLowerCase(); var c=courierRepository.save(Courier.builder().store(store).fullName(body.get("fullName")).phone(body.get("phone")).email(email==null||email.isBlank()?null:email).build()); return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id",c.getId(),"fullName",c.getFullName())); }
 @PatchMapping("/{courierId}") public void update(@AuthenticationPrincipal UserPrincipal user,@PathVariable Long storeId,@PathVariable Long courierId,@RequestBody Map<String,Object> body){ access.requireOwnedStore(user.getId(),storeId); var c=courierRepository.findByIdAndStoreIdAndDeletedAtIsNull(courierId,storeId).orElseThrow(()->new ResourceNotFoundException("Kurye",courierId)); if(body.get("active")!=null)c.setActive(Boolean.TRUE.equals(body.get("active"))); if(body.get("fullName")!=null)c.setFullName(String.valueOf(body.get("fullName"))); courierRepository.save(c); }
 @PatchMapping("/deliveries/{deliveryId}") public void assign(@AuthenticationPrincipal UserPrincipal user,@PathVariable Long storeId,@PathVariable Long deliveryId,@RequestBody Map<String,Object> body){ access.requireOwnedStore(user.getId(),storeId); var d=deliveryRepository.findById(deliveryId).orElseThrow(()->new ResourceNotFoundException("Teslimat",deliveryId)); if(!d.getSubscription().getStore().getId().equals(storeId))throw new ResourceNotFoundException("Teslimat",deliveryId); if(body.get("courierId")!=null){ long courierId=((Number)body.get("courierId")).longValue(); d.setCourier(courierRepository.findByIdAndStoreIdAndDeletedAtIsNull(courierId,storeId).orElseThrow(()->new ResourceNotFoundException("Kurye",courierId))); } if(body.get("routeSequence")!=null)d.setRouteSequence(((Number)body.get("routeSequence")).intValue()); if(body.get("deliveryType")!=null)d.setDeliveryType(String.valueOf(body.get("deliveryType"))); if(body.get("deliveryDate")!=null)d.setDeliveryDate(LocalDate.parse(String.valueOf(body.get("deliveryDate")))); if(body.get("deliveryTime")!=null)d.setDeliveryTime(LocalTime.parse(String.valueOf(body.get("deliveryTime")))); deliveryRepository.save(d); }
 private String mask(String phone){ if(phone==null||phone.length()<4)return "***"; return "*** *** "+phone.substring(phone.length()-4); }
}
