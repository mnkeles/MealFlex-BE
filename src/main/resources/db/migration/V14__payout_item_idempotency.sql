CREATE UNIQUE INDEX uq_seller_payout_items_payment
    ON seller_payout_items(payment_id)
    WHERE refund_id IS NULL;
