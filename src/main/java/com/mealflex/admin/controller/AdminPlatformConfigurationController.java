package com.mealflex.admin.controller;

import com.mealflex.admin.dto.*;
import com.mealflex.admin.service.AdminPlatformConfigurationService;
import com.mealflex.security.UserPrincipal;
import com.mealflex.user.service.AccountSecurityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/admin/platform-configuration")
@RequiredArgsConstructor
public class AdminPlatformConfigurationController {
    private final AdminPlatformConfigurationService service;
    private final AccountSecurityService security;

    @GetMapping("/commission-rules")
    public List<CommissionRuleResponse> commissionRules() { return service.listCommissionRules(); }

    @PostMapping("/commission-rules")
    public ResponseEntity<CommissionRuleResponse> createCommissionRule(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CommissionRuleRequest request,
            @RequestHeader(value = "X-Reauth-Token", required = false) String reauthToken) {
        security.requireRecentAuthentication(principal.getId(), reauthToken);
        return ResponseEntity.ok(service.createCommissionRule(principal.getId(), request));
    }

    @GetMapping("/settings")
    public Map<String, Integer> settings() { return service.listSettings(); }

    @PutMapping("/settings/{key}")
    public Map<String, Integer> updateSetting(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String key,
            @Valid @RequestBody PlatformSettingUpdateRequest request,
            @RequestHeader(value = "X-Reauth-Token", required = false) String reauthToken) {
        security.requireRecentAuthentication(principal.getId(), reauthToken);
        return service.updateSetting(principal.getId(), key, request.value());
    }
}
