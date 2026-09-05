package com.mealflex.auth.service;

import com.mealflex.auth.dto.LoginRequest;
import com.mealflex.auth.dto.RefreshTokenRequest;
import com.mealflex.auth.dto.RegisterRequest;
import com.mealflex.auth.entity.UserSession;
import com.mealflex.auth.repository.UserSessionRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.security.JwtProperties;
import com.mealflex.security.JwtTokenProvider;
import com.mealflex.security.UserPrincipal;
import com.mealflex.user.entity.Role;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.ConsentRecordRepository;
import com.mealflex.user.repository.UserRepository;
import com.mealflex.user.service.AccountSecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserSessionRepository sessionRepository;
    @Mock private ConsentRecordRepository consentRepository;
    @Mock private JwtProperties jwtProperties;
    @InjectMocks private AuthService service;

    @Test
    void customerRegistrationCreatesSessionAndNeverReturnsPassword() {
        RegisterRequest request = registerRequest(Role.CUSTOMER);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });
        when(tokenProvider.generateAccessToken(10L, request.getEmail(), "CUSTOMER")).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken(10L)).thenReturn("refresh-token");
        when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(60_000L);

        var response = service.register(request, "Test browser", "127.0.0.1");

        assertEquals(10L, response.getUserId());
        assertEquals(Role.CUSTOMER, response.getRole());
        assertEquals("access-token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        ArgumentCaptor<UserSession> session = ArgumentCaptor.forClass(UserSession.class);
        verify(sessionRepository).save(session.capture());
        assertNotEquals("refresh-token", session.getValue().getRefreshTokenHash());
        verify(consentRepository, times(2)).save(any());
    }

    @Test
    void adminCannotRegisterThroughPublicEndpointService() {
        RegisterRequest request = registerRequest(Role.ADMIN);

        assertThrows(BusinessException.class, () -> service.register(request, "browser", "127.0.0.1"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginCreatesFreshSession() {
        User user = user(11L);
        Authentication authentication = new UsernamePasswordAuthenticationToken(UserPrincipal.from(user), null, UserPrincipal.from(user).getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(tokenProvider.generateAccessToken(11L, user.getEmail(), "CUSTOMER")).thenReturn("new-access");
        when(tokenProvider.generateRefreshToken(11L)).thenReturn("new-refresh");
        when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(60_000L);

        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword("password123");

        var response = service.login(request, "browser", "127.0.0.1");

        assertEquals("new-access", response.getAccessToken());
        verify(sessionRepository).save(any(UserSession.class));
    }

    @Test
    void refreshRotatesSessionAndRejectsInvalidToken() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh");
        User user = user(12L);
        UserSession previous = UserSession.builder().user(user).refreshTokenHash(AccountSecurityService.hash("valid-refresh"))
                .lastSeenAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(tokenProvider.validateToken("valid-refresh")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid-refresh")).thenReturn(12L);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(sessionRepository.findByRefreshTokenHash(AccountSecurityService.hash("valid-refresh"))).thenReturn(Optional.of(previous));
        when(tokenProvider.generateAccessToken(12L, user.getEmail(), "CUSTOMER")).thenReturn("rotated-access");
        when(tokenProvider.generateRefreshToken(12L)).thenReturn("rotated-refresh");
        when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(60_000L);

        var response = service.refreshToken(request, "browser", "127.0.0.1");

        assertEquals("rotated-access", response.getAccessToken());
        assertNotNull(previous.getRevokedAt());
        verify(sessionRepository, times(2)).save(any(UserSession.class));
        when(tokenProvider.validateToken("expired-refresh")).thenReturn(false);
        RefreshTokenRequest expired = new RefreshTokenRequest();
        expired.setRefreshToken("expired-refresh");
        assertThrows(BusinessException.class, () -> service.refreshToken(expired, "browser", "127.0.0.1"));
    }

    @Test
    void logoutRevokesMatchingRefreshSessionAndIsIdempotent() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("logout-refresh");
        UserSession session = UserSession.builder().refreshTokenHash(AccountSecurityService.hash("logout-refresh"))
                .lastSeenAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(sessionRepository.findByRefreshTokenHash(AccountSecurityService.hash("logout-refresh"))).thenReturn(Optional.of(session));

        service.logout(request);
        service.logout(request);

        assertNotNull(session.getRevokedAt());
        verify(sessionRepository, times(1)).save(session);
    }

    private RegisterRequest registerRequest(Role role) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Test"); request.setLastName("Kullanıcı"); request.setEmail("auth-test@mealflex.local");
        request.setPassword("password123"); request.setRole(role);
        return request;
    }

    private User user(Long id) {
        User user = User.builder().email("customer@mealflex.local").password("hashed").firstName("Test").lastName("Kullanıcı").role(Role.CUSTOMER).active(true).build();
        user.setId(id);
        return user;
    }
}
