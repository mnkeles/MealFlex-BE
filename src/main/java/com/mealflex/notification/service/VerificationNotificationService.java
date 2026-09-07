package com.mealflex.notification.service;

import com.mealflex.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerificationNotificationService {
    private final NotificationEventService notifications;

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    public void sendPasswordReset(User user, String token) {
        notifications.publishExternal(user, "PASSWORD_RESET", "MealFlex şifre sıfırlama",
                "Şifrenizi yenilemek için bağlantıyı açın: " + frontendBaseUrl + "/reset-password?token=" + token
                        + " Bu bağlantı 1 saat geçerlidir.", "EMAIL");
    }

    public void sendEmailVerification(User user, String token) {
        notifications.publishExternal(user, "EMAIL_VERIFICATION", "MealFlex e-posta doğrulama",
                "E-posta adresinizi doğrulamak için bağlantıyı açın: " + frontendBaseUrl
                        + "/verify-email?token=" + token + " Bu bağlantı 1 saat geçerlidir.", "EMAIL");
    }

    public void sendPhoneOtp(User user, String otp) {
        notifications.publishExternal(user, "PHONE_OTP", "MealFlex telefon doğrulama",
                "MealFlex doğrulama kodunuz: " + otp + ". Kod 10 dakika geçerlidir.", "SMS");
    }
}
