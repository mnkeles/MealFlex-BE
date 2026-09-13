#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
compose_file="$script_dir/docker-compose.staging.yml"

if [ "$#" -ne 3 ] || [ "$3" != "ROLLBACK_STAGING" ]; then
    echo "Kullanım: sh rollback-staging.sh <.env.staging> <hedef-sürüm> ROLLBACK_STAGING" >&2
    echo "Veritabanı migration'ları geri alınmaz; yalnız uygulama imajları değiştirilir." >&2
    exit 1
fi

env_file=$1
target_version=$2
if [ "${#target_version}" -gt 64 ] ||
   ! printf '%s' "$target_version" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9._-]*$'; then
    echo "Geçersiz hedef sürüm." >&2
    exit 1
fi

sh "$script_dir/validate-staging-env.sh" "$env_file"
if ! command -v docker >/dev/null 2>&1; then
    echo "Docker bulunamadı." >&2
    exit 1
fi
docker image inspect "mealflex-backend:$target_version" >/dev/null
docker image inspect "mealflex-frontend:$target_version" >/dev/null

echo "Geri alma öncesi güvenlik yedeği alınıyor..."
sh "$script_dir/backup.sh" "$env_file" "$script_dir/backups/pre-rollback"

export APP_VERSION=$target_version
docker compose --env-file "$env_file" -f "$compose_file" up -d --no-build --wait --wait-timeout 180

if ! sh "$script_dir/verify-deployment.sh" "$env_file"; then
    echo "Hedef sürüm doğrulanamadı. Otomatik veritabanı geri yüklemesi yapılmadı." >&2
    exit 1
fi

env_temp=$(mktemp "${env_file}.XXXXXX")
cleanup() {
    rm -f -- "$env_temp"
}
trap cleanup EXIT HUP INT TERM
awk -v version="$target_version" '
    BEGIN { updated = 0 }
    /^APP_VERSION=/ { print "APP_VERSION=" version; updated = 1; next }
    { print }
    END { if (!updated) print "APP_VERSION=" version }
' "$env_file" > "$env_temp"
chmod 600 "$env_temp"
mv -- "$env_temp" "$env_file"
trap - EXIT HUP INT TERM

echo "Staging uygulama sürümü geri alındı: $target_version"
