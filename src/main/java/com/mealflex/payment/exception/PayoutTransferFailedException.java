package com.mealflex.payment.exception;

import com.mealflex.common.exception.BusinessException;

/** Sağlayıcı reddini bildirirken TRANSFER_FAILED durumunun commit edilmesini sağlar. */
public class PayoutTransferFailedException extends BusinessException {
    public PayoutTransferFailedException(String message) {
        super("PAYOUT_TRANSFER_FAILED", message);
    }
}
