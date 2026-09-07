-- Read-only: report older payments that cannot safely be assigned to deliveries.
-- Run after V43. Never fills missing financial history with guessed amounts.
SELECT p.id AS payment_id, p.subscription_id, p.idempotency_key,
       p.status, p.gross_amount, p.refunded_amount, p.net_amount,
       p.paid_at, count(a.id) AS allocation_count
FROM payments p
LEFT JOIN payment_allocations a ON a.payment_id = p.id
WHERE p.paid_at IS NOT NULL AND p.refunded_amount < p.gross_amount
GROUP BY p.id
HAVING count(a.id) = 0
ORDER BY p.subscription_id, p.id;
