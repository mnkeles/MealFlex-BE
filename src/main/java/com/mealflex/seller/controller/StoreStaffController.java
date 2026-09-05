package com.mealflex.seller.controller;
import com.mealflex.security.UserPrincipal;
import com.mealflex.seller.dto.StoreStaffResponse;
import com.mealflex.seller.service.StoreStaffService;
import lombok.RequiredArgsConstructor; import org.springframework.http.*; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*; import java.util.*;

@RestController @RequestMapping("/v1/seller/stores/{storeId}/staff") @RequiredArgsConstructor
public class StoreStaffController {
 private final StoreStaffService service;
 @GetMapping public List<StoreStaffResponse> list(@AuthenticationPrincipal UserPrincipal p,@PathVariable Long storeId){return service.list(p.getId(),storeId);}
 @PostMapping public ResponseEntity<StoreStaffResponse> invite(@AuthenticationPrincipal UserPrincipal p,@PathVariable Long storeId,@RequestBody InviteRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(service.invite(p.getId(),storeId,request.email(),request.role()));}
 @DeleteMapping("/{staffId}") public ResponseEntity<Void> deactivate(@AuthenticationPrincipal UserPrincipal p,@PathVariable Long storeId,@PathVariable Long staffId){service.cancelOrDeactivate(p.getId(),storeId,staffId);return ResponseEntity.noContent().build();}
 public record InviteRequest(String email,String role){}
}
