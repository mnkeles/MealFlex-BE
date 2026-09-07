UPDATE subscriptions
SET status = 'CANCELLED',
    cancelled_at = COALESCE(cancelled_at, CURRENT_TIMESTAMP),
    cancellation_reason = COALESCE(
        cancellation_reason,
        'Eski erteleme durumu kaldırıldığı için abonelik güvenli biçimde kapatıldı.'
    )
WHERE status = 'POSTPONED';

ALTER TABLE subscriptions DROP COLUMN IF EXISTS postponed_count;
