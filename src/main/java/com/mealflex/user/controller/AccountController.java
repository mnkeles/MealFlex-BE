package com.mealflex.user.controller;
import com.mealflex.security.UserPrincipal;
import com.mealflex.user.service.AccountSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController @RequestMapping("/v1/account") @RequiredArgsConstructor
public class AccountController {
 private final AccountSecurityService service;
 @PostMapping("/email/request") public ResponseEntity<Map<String,Object>> requestEmail(@AuthenticationPrincipal UserPrincipal p){return ResponseEntity.ok(service.requestEmailVerification(p.getId()));}
 @PostMapping("/email/verify") public ResponseEntity<Void> verifyEmail(@RequestBody CodeRequest r){service.verifyEmail(r.code());return ResponseEntity.noContent().build();}
 @PostMapping("/phone/request") public ResponseEntity<Map<String,Object>> requestPhone(@AuthenticationPrincipal UserPrincipal p){return ResponseEntity.ok(service.requestPhoneOtp(p.getId()));}
 @PostMapping("/phone/verify") public ResponseEntity<Void> verifyPhone(@AuthenticationPrincipal UserPrincipal p,@RequestBody CodeRequest r){service.verifyPhone(p.getId(),r.code());return ResponseEntity.noContent().build();}
 @GetMapping("/sessions") public List<Map<String,Object>> sessions(@AuthenticationPrincipal UserPrincipal p){return service.sessions(p.getId());}
 @DeleteMapping("/sessions/{id}") public ResponseEntity<Void> revoke(@AuthenticationPrincipal UserPrincipal p,@PathVariable Long id){service.revokeSession(p.getId(),id);return ResponseEntity.noContent().build();}
 @DeleteMapping("/sessions") public ResponseEntity<Void> revokeAll(@AuthenticationPrincipal UserPrincipal p){service.revokeAll(p.getId());return ResponseEntity.noContent().build();}
 @GetMapping("/notification-preferences") public Map<String,Boolean> preferences(@AuthenticationPrincipal UserPrincipal p){return service.preferences(p.getId());}
 @PutMapping("/notification-preferences") public Map<String,Boolean> preferences(@AuthenticationPrincipal UserPrincipal p,@RequestBody Map<String,Boolean> values){return service.updatePreferences(p.getId(),values);}
 @GetMapping("/consents") public List<Map<String,Object>> consents(@AuthenticationPrincipal UserPrincipal p){return service.consents(p.getId());}
 @PostMapping("/consents") public ResponseEntity<Void> consent(@AuthenticationPrincipal UserPrincipal p,@RequestBody ConsentRequest r,HttpServletRequest request){service.acceptConsent(p.getId(),r.documentType(),r.documentVersion(),request.getRemoteAddr());return ResponseEntity.noContent().build();}
 @PostMapping("/data-requests/export") public Map<String,Object> requestExport(@AuthenticationPrincipal UserPrincipal p){return service.requestDataExport(p.getId());}
 @GetMapping("/data-requests") public List<Map<String,Object>> requests(@AuthenticationPrincipal UserPrincipal p){return service.dataRequests(p.getId());}
 @GetMapping("/data-export") public Map<String,Object> export(@AuthenticationPrincipal UserPrincipal p){return service.exportData(p.getId());}
 @PostMapping("/reauthenticate") public Map<String,String> reauthenticate(@AuthenticationPrincipal UserPrincipal p,@RequestBody PasswordRequest r){return Map.of("reauthToken",service.reauthenticate(p.getId(),r.password()));}
 @DeleteMapping public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal p,@RequestHeader("X-Reauth-Token") String token,@RequestBody DeleteRequest r){service.requireRecentAuthentication(p.getId(),token);service.deleteAccount(p.getId(),r.confirmation());return ResponseEntity.noContent().build();}
 public record CodeRequest(String code){} public record ConsentRequest(String documentType,String documentVersion){} public record DeleteRequest(String confirmation){} public record PasswordRequest(String password){}
}
