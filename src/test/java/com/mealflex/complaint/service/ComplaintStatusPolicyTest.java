package com.mealflex.complaint.service;

import com.mealflex.common.exception.BusinessException;
import com.mealflex.complaint.entity.ComplaintStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComplaintStatusPolicyTest {
    @Test
    void sellerCannotReopenResolvedOrCloseComplaint() {
        BusinessException terminal = assertThrows(BusinessException.class,
                () -> ComplaintStatusPolicy.requireSellerTransition(ComplaintStatus.RESOLVED, ComplaintStatus.IN_REVIEW));
        assertThat(terminal.getCode()).isEqualTo("COMPLAINT_STATUS_TERMINAL");
        assertThrows(BusinessException.class,
                () -> ComplaintStatusPolicy.requireSellerTransition(ComplaintStatus.OPEN, ComplaintStatus.CLOSED));
        assertDoesNotThrow(() -> ComplaintStatusPolicy.requireSellerTransition(
                ComplaintStatus.OPEN, ComplaintStatus.IN_REVIEW));
    }

    @Test
    void invalidTextBecomesSafeBusinessValidationError() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> ComplaintStatusPolicy.parse("not-a-status"));
        assertThat(error.getCode()).isEqualTo("INVALID_COMPLAINT_STATUS");
    }
}
