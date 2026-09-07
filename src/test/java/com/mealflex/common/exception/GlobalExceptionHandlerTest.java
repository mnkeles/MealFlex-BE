package com.mealflex.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpInputMessage;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.lang.reflect.Method;
import java.util.Set;

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

    @Test
    void illegalClientValueReturnsTurkishBadRequestWithoutInternalDetails() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("secret enum implementation"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.getBody().getMessage()).doesNotContain("secret");
    }

    @Test
    void missingHeaderReturnsBadRequestAndNamesTheHeader() throws Exception {
        Method method = Fixture.class.getDeclaredMethod("endpoint", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        var response = handler.handleMissingHeader(new MissingRequestHeaderException("Idempotency-Key", parameter));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).contains("Idempotency-Key");
    }

    @Test
    void unreadableBodyTypeMismatchAndConstraintViolationReturnBadRequest() throws Exception {
        HttpInputMessage input = org.mockito.Mockito.mock(HttpInputMessage.class);
        assertThat(handler.handleUnreadableMessage(new HttpMessageNotReadableException("bad", input)).getStatusCode().value()).isEqualTo(400);
        Method method = Fixture.class.getDeclaredMethod("endpoint", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        assertThat(handler.handleTypeMismatch(new MethodArgumentTypeMismatchException("bad", Integer.class, "status", parameter, null))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(handler.handleConstraintViolation(new ConstraintViolationException(Set.of())).getStatusCode().value()).isEqualTo(400);
    }

    private static class Fixture {
        @SuppressWarnings("unused")
        void endpoint(String value) { }
    }
}
