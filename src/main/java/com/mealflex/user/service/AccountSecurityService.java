package com.mealflex.user.service;

import com.mealflex.auth.entity.*;
import com.mealflex.auth.repository.*;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.subscription.entity.SubscriptionStatus;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.entity.*;
import com.mealflex.user.repository.*;
import com.mealflex.audit.entity.AuditLog;
import com.mealflex.audit.repository.AuditLogRepository;
import com.mealflex.notification.service.VerificationNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.mealflex.security.RecentAuthenticationService;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AccountSecurityService {
    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final UserSessionRepository sessionRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final ConsentRecordRepository consentRepository;
    private final UserDataRequestRepository dataRequestRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final RecentAuthenticationService recentAuthenticationService;
    private final AuditLogRepository auditLogRepository;
    private final VerificationNotificationService verificationNotifications;

    @Value("${app.verification.expose-code:false}") private boolean exposeCode;

    @Transactional
    public Map<String,Object> requestPasswordReset(String email) {
        Optional<User> found = userRepository.findByEmail(email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
        if (found.isEmpty()) return Map.of("message", "E-posta kayıtlıysa sıfırlama bağlantısı gönderildi.");
        String token = issue(found.get(), "PASSWORD_RESET", false);
        verificationNotifications.sendPasswordReset(found.get(), token);
        return response("E-posta kayıtlıysa sıfırlama bağlantısı gönderildi.", token);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) throw new BusinessException("WEAK_PASSWORD", "Şifre en az 8 karakter olmalıdır.");
        VerificationToken token = consume(rawToken, "PASSWORD_RESET");
        token.getUser().setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(token.getUser());
        revokeAll(token.getUser().getId());
    }

    @Transactional
    public Map<String,Object> requestEmailVerification(Long userId) {
        User user = user(userId);
        if (user.isEmailVerified()) return Map.of("message", "E-posta adresiniz zaten doğrulandı.");
        String token = issue(user, "EMAIL_VERIFICATION", false);
        verificationNotifications.sendEmailVerification(user, token);
        return response("Doğrulama bağlantısı e-posta adresinize gönderildi.", token);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        VerificationToken token = consume(rawToken, "EMAIL_VERIFICATION");
        token.getUser().setEmailVerified(true); token.getUser().setEmailVerifiedAt(Instant.now()); userRepository.save(token.getUser());
    }

    @Transactional
    public Map<String,Object> requestPhoneOtp(Long userId) {
        User user = user(userId);
        if (user.getPhone() == null || user.getPhone().isBlank()) throw new BusinessException("PHONE_REQUIRED", "Önce profilinize telefon numarası ekleyin.");
        if (user.isPhoneVerified()) return Map.of("message", "Telefon numaranız zaten doğrulandı.");
        String otp = issue(user, "PHONE_OTP", true);
        verificationNotifications.sendPhoneOtp(user, otp);
        return response("Doğrulama kodu telefonunuza gönderildi.", otp);
    }

    @Transactional
    public void verifyPhone(Long userId, String otp) {
        VerificationToken token = consume(otp, "PHONE_OTP");
        if (!token.getUser().getId().equals(userId)) throw new BusinessException("INVALID_OTP", "Doğrulama kodu geçersizdir.");
        token.getUser().setPhoneVerified(true); token.getUser().setPhoneVerifiedAt(Instant.now()); userRepository.save(token.getUser());
    }

    public List<Map<String,Object>> sessions(Long userId) {
        return sessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(userId, Instant.now()).stream()
                .map(session -> { Map<String,Object> row = new LinkedHashMap<>(); row.put("id",session.getId()); row.put("deviceName",Optional.ofNullable(session.getDeviceName()).orElse("Bilinmeyen cihaz")); row.put("ipAddress",Optional.ofNullable(session.getIpAddress()).orElse("-")); row.put("lastSeenAt",session.getLastSeenAt()); row.put("createdAt",session.getCreatedAt()); return row; }).toList();
    }

    @Transactional public void revokeSession(Long userId, Long sessionId) { UserSession session=sessionRepository.findByIdAndUserId(sessionId,userId).orElseThrow(()->new ResourceNotFoundException("Oturum",sessionId)); session.setRevokedAt(Instant.now()); sessionRepository.save(session); }
    @Transactional public void revokeAll(Long userId) { sessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(userId,Instant.now()).forEach(s->{s.setRevokedAt(Instant.now());sessionRepository.save(s);}); }

    @Transactional(readOnly=true)
    public Map<String,Boolean> preferences(Long userId) { NotificationPreference p=preferenceRepository.findByUserId(userId).orElse(null); return Map.of("emailEnabled",p==null||p.isEmailEnabled(),"smsEnabled",p==null||p.isSmsEnabled(),"pushEnabled",p==null||p.isPushEnabled(),"marketingEnabled",p!=null&&p.isMarketingEnabled()); }
    @Transactional
    public Map<String,Boolean> updatePreferences(Long userId, Map<String,Boolean> values) { NotificationPreference p=preferenceRepository.findByUserId(userId).orElseGet(()->NotificationPreference.builder().user(user(userId)).build()); p.setEmailEnabled(values.getOrDefault("emailEnabled",p.isEmailEnabled())); p.setSmsEnabled(values.getOrDefault("smsEnabled",p.isSmsEnabled())); p.setPushEnabled(values.getOrDefault("pushEnabled",p.isPushEnabled())); p.setMarketingEnabled(values.getOrDefault("marketingEnabled",p.isMarketingEnabled())); preferenceRepository.save(p); return preferences(userId); }

    public List<Map<String,Object>> consents(Long userId) { return consentRepository.findByUserIdOrderByAcceptedAtDesc(userId).stream().map(c->Map.<String,Object>of("id",c.getId(),"documentType",c.getDocumentType(),"documentVersion",c.getDocumentVersion(),"acceptedAt",c.getAcceptedAt())).toList(); }
    @Transactional public void acceptConsent(Long userId,String type,String version,String ip) { if(!Set.of("TERMS","PRIVACY","MARKETING").contains(type)) throw new BusinessException("INVALID_CONSENT","Geçersiz sözleşme türü."); consentRepository.save(ConsentRecord.builder().user(user(userId)).documentType(type).documentVersion(version).acceptedAt(Instant.now()).ipAddress(ip).build()); }

    @Transactional
    public Map<String,Object> requestDataExport(Long userId) { UserDataRequest request=dataRequestRepository.save(UserDataRequest.builder().user(user(userId)).requestType("EXPORT").status("READY").requestedAt(Instant.now()).completedAt(Instant.now()).build()); auditLogRepository.save(AuditLog.builder().actorId(userId).action("USER_DATA_EXPORT_REQUESTED").entityType("USER").entityId(userId).newValue("export request created").timestamp(Instant.now()).build()); return dataRequest(request); }
    public List<Map<String,Object>> dataRequests(Long userId) { return dataRequestRepository.findByUserIdOrderByRequestedAtDesc(userId).stream().map(this::dataRequest).toList(); }
    public Map<String,Object> exportData(Long userId) { User u=user(userId); return Map.of("profile",Map.of("email",u.getEmail(),"firstName",u.getFirstName(),"lastName",u.getLastName(),"phone",Optional.ofNullable(u.getPhone()).orElse("")),"consents",consents(userId),"generatedAt",Instant.now()); }

    @Transactional
    public void deleteAccount(Long userId,String confirmation) {
        if (!"HESABIMI SIL".equals(confirmation)) throw new BusinessException("CONFIRMATION_REQUIRED", "Onay alanına HESABIMI SIL yazın.");
        if (!subscriptionRepository.findByCustomerIdAndStatusIn(userId,List.of(SubscriptionStatus.PENDING_APPROVAL,SubscriptionStatus.PAYMENT_PENDING,SubscriptionStatus.APPROVED,SubscriptionStatus.ACTIVE,SubscriptionStatus.PAYMENT_SUSPENDED),org.springframework.data.domain.Pageable.ofSize(1)).isEmpty()) throw new BusinessException("ACTIVE_SUBSCRIPTION_EXISTS","Aktif veya bekleyen abonelik varken hesap silinemez.");
        User user=user(userId); revokeAll(userId); user.setActive(false); user.setAccountDeletedAt(Instant.now()); user.setEmail("deleted+"+userId+"-"+System.currentTimeMillis()+"@mealflex.invalid"); user.setPhone(null); user.setFirstName("Silinmiş"); user.setLastName("Kullanıcı"); user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); userRepository.save(user); auditLogRepository.save(AuditLog.builder().actorId(userId).action("ACCOUNT_ANONYMIZED").entityType("USER").entityId(userId).oldValue("active=true").newValue("active=false; personal data anonymized").timestamp(Instant.now()).build());
    }

    public String reauthenticate(Long userId, String password) {
        User current = user(userId);
        if (password == null || !passwordEncoder.matches(password, current.getPassword())) throw new BusinessException("INVALID_PASSWORD", "Şifreniz doğrulanamadı.");
        return recentAuthenticationService.issue(userId);
    }

    public void requireRecentAuthentication(Long userId, String token) { recentAuthenticationService.require(userId, token); }

    private User user(Long id){return userRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Kullanıcı",id));}
    private Map<String,Object> dataRequest(UserDataRequest r){return Map.of("id",r.getId(),"requestType",r.getRequestType(),"status",r.getStatus(),"requestedAt",r.getRequestedAt());}
    private Map<String,Object> response(String message,String token){Map<String,Object> result=new LinkedHashMap<>();result.put("message",message);if(exposeCode)result.put("developmentCode",token);return result;}
    private String issue(User user,String type,boolean digits){String raw=digits?String.format("%06d",new java.security.SecureRandom().nextInt(1_000_000)):UUID.randomUUID().toString()+UUID.randomUUID(); tokenRepository.save(VerificationToken.builder().user(user).tokenType(type).tokenHash(hash(raw)).expiresAt(Instant.now().plus(digits?Duration.ofMinutes(10):Duration.ofHours(1))).build());return raw;}
    private VerificationToken consume(String raw,String type){VerificationToken token=tokenRepository.findByTokenHashAndTokenType(hash(raw==null?"":raw),type).orElseThrow(()->new BusinessException("INVALID_TOKEN","Kod veya bağlantı geçersizdir."));if(token.getUsedAt()!=null||token.getExpiresAt().isBefore(Instant.now()))throw new BusinessException("EXPIRED_TOKEN","Kod veya bağlantının süresi dolmuştur.");token.setUsedAt(Instant.now());tokenRepository.save(token);return token;}
    public static String hash(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
