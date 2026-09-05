package com.mealflex.user.service;
import com.mealflex.auth.entity.VerificationToken;
import com.mealflex.auth.entity.UserSession;
import com.mealflex.auth.repository.*;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.user.entity.User;
import com.mealflex.user.entity.UserDataRequest;
import com.mealflex.user.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountSecurityServiceTest {
 @Mock UserRepository userRepository; @Mock VerificationTokenRepository tokenRepository; @Mock UserSessionRepository sessionRepository;
 @Mock NotificationPreferenceRepository preferenceRepository; @Mock ConsentRecordRepository consentRepository; @Mock UserDataRequestRepository dataRequestRepository;
 @Mock SubscriptionRepository subscriptionRepository; @Mock PasswordEncoder passwordEncoder; @Mock AuditLogRepository auditLogRepository; @InjectMocks AccountSecurityService service;

 @Test void forgotPasswordDoesNotRevealUnknownEmail(){when(userRepository.findByEmail("none@example.com")).thenReturn(Optional.empty());var response=service.requestPasswordReset("none@example.com");assertTrue(response.get("message").toString().contains("kayıtlıysa"));verify(tokenRepository,never()).save(any());}

 @Test void passwordResetConsumesTokenChangesPasswordAndRevokesSessions(){
  User user=User.builder().email("customer@example.com").password("old-hash").firstName("Test").lastName("Müşteri").active(true).build();user.setId(5L);
  VerificationToken token=VerificationToken.builder().user(user).tokenType("PASSWORD_RESET").tokenHash(AccountSecurityService.hash("reset-token")).expiresAt(Instant.now().plusSeconds(60)).build();
  when(tokenRepository.findByTokenHashAndTokenType(AccountSecurityService.hash("reset-token"),"PASSWORD_RESET")).thenReturn(Optional.of(token));
  when(passwordEncoder.encode("NewPassword123!")).thenReturn("new-hash");
  when(sessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(eq(5L),any())).thenReturn(List.of());

  service.resetPassword("reset-token","NewPassword123!");

  assertEquals("new-hash",user.getPassword());assertNotNull(token.getUsedAt());verify(userRepository).save(user);verify(tokenRepository).save(token);
  BusinessException reused=assertThrows(BusinessException.class,()->service.resetPassword("reset-token","AnotherPassword123!"));
  assertEquals("EXPIRED_TOKEN",reused.getCode());
 }

 @Test void passwordResetRejectsExpiredTokenAndWeakPassword(){
  User user=User.builder().email("customer@example.com").password("old-hash").firstName("Test").lastName("Müşteri").active(true).build();
  VerificationToken expired=VerificationToken.builder().user(user).tokenType("PASSWORD_RESET").tokenHash(AccountSecurityService.hash("expired-token")).expiresAt(Instant.now().minusSeconds(1)).build();
  when(tokenRepository.findByTokenHashAndTokenType(AccountSecurityService.hash("expired-token"),"PASSWORD_RESET")).thenReturn(Optional.of(expired));

  BusinessException expiredError=assertThrows(BusinessException.class,()->service.resetPassword("expired-token","NewPassword123!"));
  assertEquals("EXPIRED_TOKEN",expiredError.getCode());
  BusinessException weakError=assertThrows(BusinessException.class,()->service.resetPassword("anything","short"));
  assertEquals("WEAK_PASSWORD",weakError.getCode());
  verify(userRepository,never()).save(any());
 }

 @Test void emailAndPhoneVerificationConsumeOnlyValidTokens(){
  User user=User.builder().email("customer@example.com").password("hash").firstName("Test").lastName("Müşteri").phone("5550000000").active(true).build();user.setId(5L);
  VerificationToken email=VerificationToken.builder().user(user).tokenType("EMAIL_VERIFICATION").tokenHash(AccountSecurityService.hash("email-token")).expiresAt(Instant.now().plusSeconds(60)).build();
  VerificationToken phone=VerificationToken.builder().user(user).tokenType("PHONE_OTP").tokenHash(AccountSecurityService.hash("123456")).expiresAt(Instant.now().plusSeconds(60)).build();
  when(tokenRepository.findByTokenHashAndTokenType(AccountSecurityService.hash("email-token"),"EMAIL_VERIFICATION")).thenReturn(Optional.of(email));
  when(tokenRepository.findByTokenHashAndTokenType(AccountSecurityService.hash("123456"),"PHONE_OTP")).thenReturn(Optional.of(phone));

  service.verifyEmail("email-token");
  service.verifyPhone(5L,"123456");

  assertTrue(user.isEmailVerified());assertTrue(user.isPhoneVerified());assertNotNull(email.getUsedAt());assertNotNull(phone.getUsedAt());
  BusinessException reused=assertThrows(BusinessException.class,()->service.verifyPhone(5L,"123456"));
  assertEquals("EXPIRED_TOKEN",reused.getCode());
 }

 @Test void expiredOrUnknownOtpCannotVerifyPhone(){
  User user=User.builder().email("customer@example.com").password("hash").firstName("Test").lastName("Müşteri").active(true).build();user.setId(5L);
  VerificationToken expired=VerificationToken.builder().user(user).tokenType("PHONE_OTP").tokenHash(AccountSecurityService.hash("654321")).expiresAt(Instant.now().minusSeconds(1)).build();
  when(tokenRepository.findByTokenHashAndTokenType(AccountSecurityService.hash("654321"),"PHONE_OTP")).thenReturn(Optional.of(expired));

  assertEquals("EXPIRED_TOKEN",assertThrows(BusinessException.class,()->service.verifyPhone(5L,"654321")).getCode());
  assertEquals("INVALID_TOKEN",assertThrows(BusinessException.class,()->service.verifyPhone(5L,"000000")).getCode());
  verify(userRepository,never()).save(any());
 }

 @Test void sessionsListDoesNotExposeRefreshTokenHash(){
  User user=User.builder().email("customer@example.com").password("hash").firstName("Test").lastName("Müşteri").active(true).build();user.setId(5L);
  UserSession session=UserSession.builder().user(user).refreshTokenHash("sensitive-hash").deviceName("Chrome").ipAddress("127.0.0.1").lastSeenAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();session.setId(9L);
  when(sessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(eq(5L),any())).thenReturn(List.of(session));

  var sessions=service.sessions(5L);

  assertEquals(1,sessions.size());assertEquals("Chrome",sessions.getFirst().get("deviceName"));assertFalse(sessions.getFirst().containsKey("refreshTokenHash"));assertFalse(sessions.getFirst().containsKey("expiresAt"));
 }

 @Test void sessionRevocationRequiresOwnershipAndSupportsAllSessions(){
  User user=User.builder().email("customer@example.com").password("hash").firstName("Test").lastName("Müşteri").active(true).build();user.setId(5L);
  UserSession owned=UserSession.builder().user(user).refreshTokenHash("hash").lastSeenAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();owned.setId(9L);
  when(sessionRepository.findByIdAndUserId(9L,5L)).thenReturn(Optional.of(owned));
  when(sessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(eq(5L),any())).thenReturn(List.of(owned));

  service.revokeSession(5L,9L);service.revokeAll(5L);

  assertNotNull(owned.getRevokedAt());verify(sessionRepository,times(2)).save(owned);
  assertThrows(com.mealflex.common.exception.ResourceNotFoundException.class,()->service.revokeSession(5L,99L));
 }

 @Test void accountCannotBeDeletedWhileSubscriptionIsActive(){when(subscriptionRepository.findByCustomerIdAndStatusIn(eq(5L),anyList(),any())).thenReturn(new PageImpl<>(List.of(mock(Subscription.class))));assertThrows(BusinessException.class,()->service.deleteAccount(5L,"HESABIMI SIL"));verify(userRepository,never()).save(any());}

 @Test void phoneOtpCannotVerifyAnotherUser(){User tokenUser=mock(User.class);when(tokenUser.getId()).thenReturn(8L);VerificationToken token=VerificationToken.builder().user(tokenUser).tokenType("PHONE_OTP").tokenHash("hash").expiresAt(Instant.now().plusSeconds(60)).build();when(tokenRepository.findByTokenHashAndTokenType(AccountSecurityService.hash("123456"),"PHONE_OTP")).thenReturn(Optional.of(token));assertThrows(BusinessException.class,()->service.verifyPhone(5L,"123456"));verify(userRepository,never()).save(any());}

 @Test void notificationPreferencesHaveSafeDefaults(){when(preferenceRepository.findByUserId(5L)).thenReturn(Optional.empty());var preferences=service.preferences(5L);assertTrue(preferences.get("emailEnabled"));assertTrue(preferences.get("smsEnabled"));assertFalse(preferences.get("marketingEnabled"));}

 @Test void dataExportDoesNotExposePasswordAndCreatesAuditRecord(){
  User user=User.builder().email("customer@example.com").password("secret-hash").firstName("Test").lastName("Müşteri").phone("5550000000").active(true).build();user.setId(5L);
  UserDataRequest export=UserDataRequest.builder().user(user).requestType("EXPORT").status("READY").requestedAt(Instant.now()).completedAt(Instant.now()).build();export.setId(7L);
  when(userRepository.findById(5L)).thenReturn(Optional.of(user));when(dataRequestRepository.save(any())).thenReturn(export);

  var request=service.requestDataExport(5L);var data=service.exportData(5L);

  assertEquals(7L,request.get("id"));assertFalse(data.toString().contains("secret-hash"));assertEquals("customer@example.com",((Map<?,?>)data.get("profile")).get("email"));
  verify(auditLogRepository).save(argThat(a->a.getActorId().equals(5L)&&"USER_DATA_EXPORT_REQUESTED".equals(a.getAction())));
 }

 @Test void accountDeletionAnonymizesPersonalDataRevokesSessionsAndAudits(){
  User user=User.builder().email("customer@example.com").password("secret-hash").firstName("Test").lastName("Müşteri").phone("5550000000").active(true).build();user.setId(5L);
  when(subscriptionRepository.findByCustomerIdAndStatusIn(eq(5L),anyList(),any())).thenReturn(new PageImpl<>(List.of()));when(userRepository.findById(5L)).thenReturn(Optional.of(user));when(sessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(eq(5L),any())).thenReturn(List.of());when(passwordEncoder.encode(anyString())).thenReturn("anonymized-password");

  service.deleteAccount(5L,"HESABIMI SIL");

  assertFalse(user.isActive());assertNotNull(user.getAccountDeletedAt());assertNull(user.getPhone());assertEquals("Silinmiş",user.getFirstName());assertTrue(user.getEmail().startsWith("deleted+5-"));assertEquals("anonymized-password",user.getPassword());
  verify(auditLogRepository).save(argThat(a->a.getActorId().equals(5L)&&"ACCOUNT_ANONYMIZED".equals(a.getAction())&&!a.getNewValue().contains("customer@example.com")));
 }
}
