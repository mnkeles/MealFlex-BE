package com.mealflex.support.service;

import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.support.dto.CreateSupportRequest;
import com.mealflex.support.dto.SupportRequestResponse;
import com.mealflex.support.entity.SupportRequest;
import com.mealflex.support.repository.SupportRequestRepository;
import com.mealflex.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupportRequestService {
    private final SupportRequestRepository requests;
    private final UserRepository users;
    private final SupportEmailSender emailSender;

    @Transactional
    public SupportRequestResponse create(Long userId, CreateSupportRequest input) {
        var user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", userId));
        SupportRequest request = requests.save(SupportRequest.builder()
                .user(user)
                .accountRole(user.getRole().name())
                .contactName(input.contactName().trim())
                .contactEmail(input.contactEmail().trim().toLowerCase())
                .contactPhone(blankToNull(input.contactPhone()))
                .category(input.category())
                .subject(input.subject().trim())
                .message(input.message().trim())
                .emailStatus("PENDING")
                .nextEmailAttemptAt(Instant.now())
                .build());
        deliver(request);
        request = requests.save(request);
        return new SupportRequestResponse(request.getId(), request.getEmailStatus(), request.getCreatedAt());
    }

    @Scheduled(fixedDelayString = "${app.support.dispatch-delay-ms:60000}")
    @Transactional
    public void retryPending() {
        requests.findTop50ByEmailStatusInAndNextEmailAttemptAtLessThanEqualOrderByCreatedAtAsc(
                List.of("PENDING", "RETRY"), Instant.now()).forEach(request -> {
                    deliver(request);
                    requests.save(request);
                });
    }

    private void deliver(SupportRequest request) {
        try {
            emailSender.send(request);
            request.setEmailStatus("SENT");
            request.setEmailSentAt(Instant.now());
            request.setNextEmailAttemptAt(null);
            request.setLastEmailError(null);
        } catch (RuntimeException exception) {
            int attempts = request.getEmailAttempts() + 1;
            request.setEmailAttempts(attempts);
            request.setEmailStatus(attempts >= 5 ? "FAILED" : "RETRY");
            request.setNextEmailAttemptAt(attempts >= 5 ? null
                    : Instant.now().plus(Duration.ofMinutes(Math.min(60, 1L << (attempts - 1)))));
            request.setLastEmailError(safeError(exception));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String safeError(RuntimeException exception) {
        String value = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}

