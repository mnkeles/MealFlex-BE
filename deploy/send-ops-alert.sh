#!/usr/bin/env sh
set -eu

service_name=${1:-unknown}
case "$service_name" in
    *[!A-Za-z0-9@_.:-]*)
        echo "Geçersiz servis adı." >&2
        exit 1
        ;;
esac

if [ -z "${OPS_ALERT_WEBHOOK_URL:-}" ]; then
    echo "OPS_ALERT_WEBHOOK_URL tanımlı değil; $service_name hatası yalnız systemd journal'a yazıldı." >&2
    exit 0
fi

occurred_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
payload=$(printf '{"source":"mealflex","severity":"critical","service":"%s","occurredAt":"%s","message":"MealFlex operasyon kontrolü başarısız oldu."}' \
    "$service_name" "$occurred_at")

curl --fail --silent --show-error --max-time 15 \
    --header 'Content-Type: application/json' \
    --request POST \
    --data "$payload" \
    "$OPS_ALERT_WEBHOOK_URL" >/dev/null

echo "$service_name hatası operasyon webhook'una gönderildi."
