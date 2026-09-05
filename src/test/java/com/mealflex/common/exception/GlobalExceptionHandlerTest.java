package com.mealflex.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void missingResourceReturnsSafeTurkishNotFoundWithoutRecordDetails() {
        var response = handler.handleNotFound(new ResourceNotFoundException("Abonelik", 999L));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("İstenen kayıt bulunamadı.");
        assertThat(response.getBody().getMessage()).doesNotContain("999", "Abonelik");
    }

    @Test
    void unexpectedFailureDoesNotExposeInternalSecretToClient() {
        var response = handler.handleGenericException(new IllegalStateException("token=very-secret"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).isEqualTo("Beklenmeyen bir hata oluştu.");
        assertThat(response.getBody().getMessage()).doesNotContain("very-secret");
    }
}
