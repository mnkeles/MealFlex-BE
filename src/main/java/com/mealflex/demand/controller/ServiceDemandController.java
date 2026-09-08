package com.mealflex.demand.controller;

import com.mealflex.demand.dto.ServiceDemandResponse;
import com.mealflex.demand.service.ServiceDemandService;
import com.mealflex.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/service-demands")
@RequiredArgsConstructor
public class ServiceDemandController {
    private final ServiceDemandService service;
    @PostMapping
    public ResponseEntity<ServiceDemandResponse> register(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam Long addressId) {
        return ResponseEntity.ok(service.register(principal.getId(), addressId));
    }
}
