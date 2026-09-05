package com.mealflex.auth.controller;

import com.mealflex.auth.dto.*;
import com.mealflex.auth.service.AuthService;
import com.mealflex.security.UserPrincipal;
import com.mealflex.user.service.AccountSecurityService;
import com.mealflex.security.LoginRateLimitService;
import com.mealflex.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Kimlik doğrulama işlemleri")
public class AuthController {

    private final AuthService authService;
    private final AccountSecurityService accountSecurityService;
    private final LoginRateLimitService loginRateLimitService;

    @PostMapping("/register")
    @Operation(summary = "Yeni kullanıcı kaydı")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        AuthResponse response = authService.register(request, http.getHeader("User-Agent"), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Kullanıcı girişi")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        loginRateLimitService.check(request.getEmail(), http.getRemoteAddr());
        try {
            AuthResponse response = authService.login(request, http.getHeader("User-Agent"), http.getRemoteAddr());
            loginRateLimitService.success(request.getEmail(), http.getRemoteAddr());
            return ResponseEntity.ok(response);
        } catch (BusinessException exception) {
            loginRateLimitService.fail(request.getEmail(), http.getRemoteAddr());
            throw exception;
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "Access token yenileme")
    public ResponseEntity<AuthResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
        AuthResponse response = authService.refreshToken(request, http.getHeader("User-Agent"), http.getRemoteAddr());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Refresh token oturumunu kapat")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    @Operation(summary = "Şifre değiştirme")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<java.util.Map<String,Object>> forgotPassword(@RequestBody EmailRequest request) {
        return ResponseEntity.ok(accountSecurityService.requestPasswordReset(request.email()));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        accountSecurityService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    public record EmailRequest(String email) {}
    public record ResetPasswordRequest(String token, String newPassword) {}
}
