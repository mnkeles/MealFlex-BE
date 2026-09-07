package com.mealflex.platform.controller;
import com.mealflex.platform.dto.FeatureFlagResponse; import com.mealflex.platform.service.FeatureFlagService; import com.mealflex.security.UserPrincipal; import lombok.RequiredArgsConstructor; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequiredArgsConstructor public class FeatureFlagController {private final FeatureFlagService service;
 @GetMapping("/v1/feature-flags") public Map<String,Boolean> flags(@AuthenticationPrincipal UserPrincipal p){return service.publicFlags(p.getId());}
 @PostMapping("/v1/analytics/events") public void track(@AuthenticationPrincipal UserPrincipal p,@RequestBody Map<String,String> body){service.track(p.getId(),body.get("name"),body.get("properties"));}
 @GetMapping("/v1/admin/feature-flags") public List<FeatureFlagResponse> all(){return service.list().stream().map(FeatureFlagResponse::from).toList();}
 @PutMapping("/v1/admin/feature-flags/{key}") public FeatureFlagResponse set(@PathVariable String key,@RequestBody Map<String,Object> body){return FeatureFlagResponse.from(service.upsert(key,(String)body.getOrDefault("description",""),Boolean.TRUE.equals(body.get("enabled")),((Number)body.getOrDefault("rolloutPercent",0)).intValue()));}
}
