#!/usr/bin/env sh
set -eu

env_file=${1:-.env.staging}

if [ ! -f "$env_file" ]; then
    echo "$env_file bulunamadı. .env.staging.example dosyasını kopyalayıp düzenleyin." >&2
    exit 1
fi

read_value() {
    key=$1
    sed -n "s/^${key}=//p" "$env_file" | tail -n 1
}

failed=0
for key in DB_NAME DB_USERNAME DB_PASSWORD APP_VERSION PUBLIC_HOST PUBLIC_BASE_URL TLS_EMAIL JWT_SECRET PAYMENT_WEBHOOK_SECRET; do
    value=$(read_value "$key")
    if [ -z "$value" ]; then
        echo "$key eksik." >&2
        failed=1
    fi
done

app_version=$(read_value APP_VERSION)
if [ "${#app_version}" -gt 64 ] ||
   ! printf '%s' "$app_version" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9._-]*$'; then
    echo "APP_VERSION 1-64 karakter olmalı, harf/rakamla başlamalı ve yalnız harf, rakam, nokta, tire veya alt çizgi içermeli." >&2
    failed=1
fi

for key in DB_PASSWORD JWT_SECRET PAYMENT_WEBHOOK_SECRET; do
    value=$(read_value "$key")
    invalid=0
    if printf '%s' "$value" | grep -Eiq 'replace|example|password'; then
        invalid=1
    fi
    if [ "${#value}" -lt 32 ] || [ "$invalid" -eq 1 ]; then
        echo "$key en az 32 karakterlik gerçek bir rastgele değer olmalı." >&2
        failed=1
    fi
done

jwt_secret=$(read_value JWT_SECRET)
webhook_secret=$(read_value PAYMENT_WEBHOOK_SECRET)
if [ -n "$jwt_secret" ] && [ "$jwt_secret" = "$webhook_secret" ]; then
    echo "JWT_SECRET ve PAYMENT_WEBHOOK_SECRET birbirinden farklı olmalı." >&2
    failed=1
fi

public_host=$(read_value PUBLIC_HOST)
public_base_url=$(read_value PUBLIC_BASE_URL)
case "$public_host" in
    localhost|*[!A-Za-z0-9.-]*|.*|*.)
        echo "PUBLIC_HOST geçerli, dışarıdan erişilebilir bir alan adı olmalı." >&2
        failed=1
        ;;
esac
case "$public_host" in
    *.*) ;;
    *)
        echo "PUBLIC_HOST tam alan adı olmalı." >&2
        failed=1
        ;;
esac
if printf '%s' "$public_host" | grep -Eq '^[0-9.]+$'; then
    echo "PUBLIC_HOST IP adresi değil alan adı olmalı." >&2
    failed=1
fi

if [ "$public_base_url" != "https://$public_host" ]; then
    echo "PUBLIC_BASE_URL, PUBLIC_HOST için kök HTTPS adresi olmalı." >&2
    failed=1
fi

tls_email=$(read_value TLS_EMAIL)
case "$tls_email" in
    *@*.*) ;;
    *)
        echo "TLS_EMAIL geçerli bir e-posta adresi olmalı." >&2
        failed=1
        ;;
esac

backup_max_age_hours=$(read_value BACKUP_MAX_AGE_HOURS)
disk_warn_percent=$(read_value DISK_USAGE_WARN_PERCENT)
backup_max_age_hours=${backup_max_age_hours:-26}
disk_warn_percent=${disk_warn_percent:-80}
case "$backup_max_age_hours:$disk_warn_percent" in
    *[!0-9:]*)
        echo "BACKUP_MAX_AGE_HOURS ve DISK_USAGE_WARN_PERCENT tam sayı olmalı." >&2
        failed=1
        ;;
esac
if [ "$failed" -eq 0 ] &&
   { [ "$backup_max_age_hours" -lt 1 ] || [ "$backup_max_age_hours" -gt 168 ] ||
     [ "$disk_warn_percent" -lt 1 ] || [ "$disk_warn_percent" -gt 99 ]; }; then
    echo "Yedek yaşı 1-168 saat, disk eşiği 1-99 arasında olmalı." >&2
    failed=1
fi

if [ "$failed" -ne 0 ]; then
    exit 1
fi

echo "Staging yapılandırması geçerli; hiçbir secret değeri ekrana yazdırılmadı."
