package com.mealflex.subscription.dto;

import com.mealflex.common.validation.RejectionReasonPolicy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SellerRejectionReasonTest {
    @Test
    void exposesTenSafeCustomerFacingReasons() {
        assertThat(SellerRejectionReason.values()).hasSize(10);
        assertThat(Arrays.stream(SellerRejectionReason.values())
                .map(SellerRejectionReason::customerMessage))
                .allMatch(message -> !RejectionReasonPolicy.containsInappropriateContent(message))
                .doesNotHaveDuplicates();
    }
}
