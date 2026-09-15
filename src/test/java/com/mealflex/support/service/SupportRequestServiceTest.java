package com.mealflex.support.service;

import com.mealflex.support.dto.CreateSupportRequest;
import com.mealflex.support.entity.SupportRequest;
import com.mealflex.support.repository.SupportRequestRepository;
import com.mealflex.user.entity.Role;
import com.mealflex.user.entity.User;
import com.mealflex.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportRequestServiceTest {
    @Mock SupportRequestRepository requests;
    @Mock UserRepository users;
    @Mock SupportEmailSender emailSender;

    @Test
    void requestIsStoredAndSentToSupportEmail() {
        User user = User.builder().email("seller@example.com").password("x")
                .firstName("Ayşe").lastName("Demir").role(Role.SELLER).build();
        user.setId(7L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(requests.save(any(SupportRequest.class))).thenAnswer(invocation -> {
            SupportRequest value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(42L);
            if (value.getCreatedAt() == null) value.setCreatedAt(Instant.now());
            return value;
        });

        var response = new SupportRequestService(requests, users, emailSender).create(7L,
                new CreateSupportRequest(" Ayşe Demir ", "AYSE@EXAMPLE.COM", "+90 555 111 22 33",
                        "STORE", "Mağaza yayını", "Mağazamı yayına alırken hata görüyorum."));

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.emailStatus()).isEqualTo("SENT");
        var request = org.mockito.ArgumentCaptor.forClass(SupportRequest.class);
        verify(emailSender).send(request.capture());
        assertThat(request.getValue().getContactEmail()).isEqualTo("ayse@example.com");
        assertThat(request.getValue().getAccountRole()).isEqualTo("SELLER");
    }

    @Test
    void temporaryEmailFailureKeepsRequestForRetry() {
        User user = User.builder().email("customer@example.com").password("x")
                .firstName("Ali").lastName("Yılmaz").role(Role.CUSTOMER).build();
        user.setId(8L);
        when(users.findById(8L)).thenReturn(Optional.of(user));
        when(requests.save(any(SupportRequest.class))).thenAnswer(invocation -> {
            SupportRequest value = invocation.getArgument(0);
            value.setId(43L);
            value.setCreatedAt(Instant.now());
            return value;
        });
        doThrow(new IllegalStateException("relay unavailable")).when(emailSender).send(any());

        var response = new SupportRequestService(requests, users, emailSender).create(8L,
                new CreateSupportRequest("Ali Yılmaz", "ali@example.com", "", "PAYMENT",
                        "Ödeme sorunu", "Ödeme ekranında hata alıyorum, yardım eder misiniz?"));

        assertThat(response.emailStatus()).isEqualTo("RETRY");
        var request = org.mockito.ArgumentCaptor.forClass(SupportRequest.class);
        verify(requests, atLeastOnce()).save(request.capture());
        assertThat(request.getValue().getNextEmailAttemptAt()).isNotNull();
        assertThat(request.getValue().getLastEmailError()).contains("relay unavailable");
    }
}

