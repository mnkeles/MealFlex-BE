package com.mealflex.support.service;

import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.support.dto.CreateSupportRequest;
import com.mealflex.support.dto.UpdateSupportRequest;
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
    @Mock NotificationEventService notifications;

    @Test
    void requestIsStoredForAdminReview() {
        User user = user(7L, "Ayşe", "Demir", Role.SELLER);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(requests.save(any(SupportRequest.class))).thenAnswer(invocation -> saved(invocation.getArgument(0), 42L));

        var response = service().create(7L,
                new CreateSupportRequest(" Ayşe Demir ", "AYSE@EXAMPLE.COM", "+90 555 111 22 33",
                        "STORE", "Mağaza yayını", "Mağazamı yayına alırken hata görüyorum."));

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.status()).isEqualTo("NEW");
        var request = org.mockito.ArgumentCaptor.forClass(SupportRequest.class);
        verify(requests).save(request.capture());
        assertThat(request.getValue().getContactEmail()).isEqualTo("ayse@example.com");
        verifyNoInteractions(notifications);
    }

    @Test
    void adminAnswerCreatesOnlyInAppNotification() {
        User customer = user(8L, "Ali", "Yılmaz", Role.CUSTOMER);
        User admin = user(1L, "Admin", "Kullanıcı", Role.ADMIN);
        SupportRequest request = SupportRequest.builder().user(customer).accountRole("CUSTOMER")
                .contactName("Ali Yılmaz").contactEmail("ali@example.com").category("PAYMENT")
                .subject("Ödeme sorunu").message("Ödeme ekranında hata alıyorum.").status("NEW").build();
        request.setId(43L);
        request.setCreatedAt(Instant.now());
        when(requests.findById(43L)).thenReturn(Optional.of(request));
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(requests.save(request)).thenReturn(request);

        var response = service().updateByAdmin(1L, 43L,
                new UpdateSupportRequest("ANSWERED", "Kartınızı yeniden ekleyerek deneyebilirsiniz."));

        assertThat(response.status()).isEqualTo("ANSWERED");
        assertThat(response.adminResponse()).contains("Kartınızı");
        verify(notifications).publishInApp(customer, "SUPPORT_REQUEST", "Destek talebiniz yanıtlandı",
                "Kartınızı yeniden ekleyerek deneyebilirsiniz.", "SUPPORT_REQUEST", 43L);
    }

    private SupportRequestService service() {
        return new SupportRequestService(requests, users, notifications);
    }

    private static User user(Long id, String firstName, String lastName, Role role) {
        User user = User.builder().email(firstName.toLowerCase() + "@example.com").password("x")
                .firstName(firstName).lastName(lastName).role(role).build();
        user.setId(id);
        return user;
    }

    private static SupportRequest saved(SupportRequest value, Long id) {
        value.setId(id);
        value.setCreatedAt(Instant.now());
        return value;
    }
}
