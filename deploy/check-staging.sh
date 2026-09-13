#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
env_file=${1:-"$script_dir/.env.staging"}
backup_dir=${2:-"$script_dir/backups"}
compose_file="$script_dir/docker-compose.staging.yml"

if [ ! -f "$env_file" ]; then
    echo "CRITICAL ortam dosyası bulunamadı: $env_file" >&2
    exit 2
fi

sh "$script_dir/validate-staging-env.sh" "$env_file" >/dev/null

read_value() {
    key=$1
    sed -n "s/^${key}=//p" "$env_file" | tail -n 1
}

public_host=$(read_value PUBLIC_HOST)
public_base_url=$(read_value PUBLIC_BASE_URL)
backup_max_age_hours=$(read_value BACKUP_MAX_AGE_HOURS)
disk_warn_percent=$(read_value DISK_USAGE_WARN_PERCENT)
backup_max_age_hours=${backup_max_age_hours:-26}
disk_warn_percent=${disk_warn_percent:-80}

case "$backup_max_age_hours:$disk_warn_percent" in
    *[!0-9:]*)
        echo "CRITICAL izleme eşikleri pozitif tam sayı olmalı." >&2
        exit 2
        ;;
esac
if [ "$backup_max_age_hours" -lt 1 ] || [ "$disk_warn_percent" -lt 1 ] || [ "$disk_warn_percent" -gt 99 ]; then
    echo "CRITICAL geçersiz izleme eşiği." >&2
    exit 2
fi

for command_name in docker curl openssl sha256sum; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "CRITICAL $command_name bulunamadı." >&2
        exit 2
    fi
done

for service in postgres backend frontend edge; do
    container_id=$(docker compose --env-file "$env_file" -f "$compose_file" ps -q "$service")
    if [ -z "$container_id" ] ||
       [ "$(docker inspect --format '{{.State.Status}}' "$container_id")" != "running" ] ||
       [ "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id")" != "healthy" ]; then
        echo "CRITICAL $service healthy değil." >&2
        exit 2
    fi
done

if ! curl --fail --silent --show-error --max-time 15 \
    "$public_base_url/api/actuator/health/readiness" |
    grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    echo "CRITICAL backend readiness başarısız." >&2
    exit 2
fi

if ! printf '' | openssl s_client -connect "$public_host:443" -servername "$public_host" 2>/dev/null |
    openssl x509 -noout -checkend 1209600 >/dev/null; then
    echo "CRITICAL TLS sertifikası 14 günden kısa sürede bitiyor veya okunamıyor." >&2
    exit 2
fi

disk_usage=$(df -P "$script_dir" | awk 'NR == 2 { gsub(/%/, "", $5); print $5 }')
if [ -z "$disk_usage" ] || [ "$disk_usage" -ge "$disk_warn_percent" ]; then
    echo "CRITICAL disk kullanımı eşiği aştı: ${disk_usage:-bilinmiyor}%" >&2
    exit 2
fi

latest_manifest=$(find "$backup_dir" -maxdepth 1 -type f -name 'mealflex-staging-*.sha256' \
    -mmin "-$((backup_max_age_hours * 60))" -print 2>/dev/null | sort | tail -n 1)
if [ -z "$latest_manifest" ]; then
    echo "CRITICAL son $backup_max_age_hours saat içinde geçerli yedek manifesti yok." >&2
    exit 2
fi
(
    cd -- "$(dirname -- "$latest_manifest")"
    sha256sum -c "$(basename -- "$latest_manifest")" >/dev/null
) || {
    echo "CRITICAL son yedeğin SHA-256 kontrolü başarısız." >&2
    exit 2
}

echo "OK staging healthy; TLS, disk ve yedek kontrolleri geçti."
