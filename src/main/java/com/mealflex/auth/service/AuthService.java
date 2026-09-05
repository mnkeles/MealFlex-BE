package com.mealflex.auth.service;

import com.mealflex.auth.dto.*;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.security.JwtTokenProvider;
import com.mealflex.security.UserPrincipal;
import com.mealflex.user.entity.Role;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import com.mealflex.auth.entity.UserSession;
import com.mealflex.auth.repository.UserSessionRepository;
import com.mealflex.user.entity.ConsentRecord;
import com.mealflex.user.repository.ConsentRecordRepository;
import com.mealflex.security.JwtProperties;
import com.mealflex.user.service.AccountSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserSessionRepository sessionRepository;
    private final ConsentRecordRepository consentRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public AuthResponse register(RegisterRequest request, String deviceName, String ipAddress) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("EMAIL_ALREADY_EXISTS",
                    "Bu e-posta adresi zaten kayıtlı.",
                    HttpStatus.CONFLICT);
        }

        if (request.getRole() == Role.ADMIN) {
            throw new BusinessException("INVALID_ROLE",
                    "Admin rolü ile kayıt olunamaz.",
                    HttpStatus.FORBIDDEN);
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(request.getRole())
                .build();

        user = userRepository.save(user);
        java.time.Instant now = java.time.Instant.now();
        user.setTermsAcceptedAt(now); user.setTermsVersion("2026-08");
        user.setPrivacyAcceptedAt(now); user.setPrivacyVersion("2026-08");
        user = userRepository.save(user);
        consentRepository.save(ConsentRecord.builder().user(user).documentType("TERMS").documentVersion("2026-08").acceptedAt(now).ipAddress(ipAddress).build());
        consentRepository.save(ConsentRecord.builder().user(user).documentType("PRIVACY").documentVersion("2026-08").acceptedAt(now).ipAddress(ipAddress).build());
        log.info("New user registered: {} with role {}", user.getEmail(), user.getRole());

        return buildAuthResponse(user, deviceName, ipAddress);
    }

    public AuthResponse login(LoginRequest request, String deviceName, String ipAddress) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "Kullanıcı bulunamadı."));

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user, deviceName, ipAddress);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request, String deviceName, String ipAddress) {
        String refreshToken = request.getRefreshToken();

        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BusinessException("INVALID_REFRESH_TOKEN",
                    "Geçersiz veya süresi dolmuş refresh token.",
                    HttpStatus.UNAUTHORIZED);
        }

        Long userId = tokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "Kullanıcı bulunamadı."));

        UserSession session = sessionRepository.findByRefreshTokenHash(AccountSecurityService.hash(refreshToken))
                .filter(value -> value.getRevokedAt() == null && value.getExpiresAt().isAfter(java.time.Instant.now()) && value.getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException("SESSION_REVOKED", "Oturum kapatılmış veya süresi dolmuş.", HttpStatus.UNAUTHORIZED));
        session.setRevokedAt(java.time.Instant.now());
        sessionRepository.save(session);
        return buildAuthResponse(user, deviceName == null ? session.getDeviceName() : deviceName, ipAddress);
    }

    /**
     * Refresh token oturumunu iptal eder. İstek idempotent tutulur; böylece
     * tarayıcı geçersiz veya süresi dolmuş token ile de güvenle çıkış yapabilir.
     */
    @Transactional
    public void logout(RefreshTokenRequest request) {
        sessionRepository.findByRefreshTokenHash(AccountSecurityService.hash(request.getRefreshToken()))
                .filter(session -> session.getRevokedAt() == null)
                .ifPresent(session -> {
                    session.setRevokedAt(java.time.Instant.now());
                    sessionRepository.save(session);
                });
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "Kullanıcı bulunamadı."));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("INVALID_CURRENT_PASSWORD",
                    "Mevcut şifre hatalı.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for user: {}", user.getEmail());
    }

    private AuthResponse buildAuthResponse(User user, String deviceName, String ipAddress) {
        String accessToken = tokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());
        sessionRepository.save(UserSession.builder().user(user)
                .refreshTokenHash(AccountSecurityService.hash(refreshToken))
                .deviceName(deviceName == null || deviceName.isBlank() ? "Web cihazı" : deviceName.substring(0, Math.min(deviceName.length(), 200)))
                .ipAddress(ipAddress)
                .lastSeenAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusMillis(jwtProperties.getRefreshTokenExpirationMs()))
                .build());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .build();
    }
}
