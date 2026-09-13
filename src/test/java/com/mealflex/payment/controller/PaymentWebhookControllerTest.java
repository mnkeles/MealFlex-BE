package com.mealflex.payment.controller;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.payment.provider.IyzicoWebhookVerifier;
import com.mealflex.payment.service.PaymentCheckoutService;
import com.mealflex.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookControllerTest {
    @Mock PaymentService paymentService;
    @Mock IyzicoWebhookVerifier verifier;
    @Mock PaymentCheckoutService checkoutService;
    @InjectMocks PaymentWebhookController controller;

    @Test
    void successfulCheckoutWebhookCompletesPaymentBeforeRecordingEvent() {
        String payload = "{checkout}";
        when(verifier.verify(payload, "signature")).thenReturn(true);
        when(verifier.isCheckoutForm(payload)).thenReturn(true);
        when(verifier.status(payload)).thenReturn("SUCCESS");
        when(verifier.token(payload)).thenReturn("checkout-token");
        when(verifier.eventId(payload)).thenReturn("event-1");
        when(verifier.eventType(payload)).thenReturn("CHECKOUT_FORM_AUTH");
        when(checkoutService.complete("checkout-token"))
                .thenReturn(new PaymentCheckoutService.CallbackResult(4L, true));
        when(paymentService.acceptVerifiedWebhook("IYZICO", "event-1", "CHECKOUT_FORM_AUTH", payload))
                .thenReturn(true);

        var response = controller.receive("iyzico", null, null, null,
                "signature", payload);

        assertThat(response.getBody()).containsEntry("processed", true);
        var ordered = inOrder(checkoutService, paymentService);
        ordered.verify(checkoutService).complete("checkout-token");
        ordered.verify(paymentService).acceptVerifiedWebhook("IYZICO", "event-1",
                "CHECKOUT_FORM_AUTH", payload);
    }

    @Test
    void failedCheckoutCompletionDoesNotConsumeWebhookEvent() {
        String payload = "{checkout}";
        when(verifier.verify(payload, "signature")).thenReturn(true);
        when(verifier.isCheckoutForm(payload)).thenReturn(true);
        when(verifier.status(payload)).thenReturn("SUCCESS");
        when(verifier.token(payload)).thenReturn("checkout-token");
        when(checkoutService.complete("checkout-token"))
                .thenReturn(new PaymentCheckoutService.CallbackResult(4L, false));

        assertThatThrownBy(() -> controller.receive("iyzico", null, null, null,
                "signature", payload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("henüz tamamlanamadı");
        verify(paymentService, never()).acceptVerifiedWebhook(anyString(), anyString(), anyString(), anyString());
    }
}
