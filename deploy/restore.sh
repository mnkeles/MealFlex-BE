#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
compose_file="$script_dir/docker-compose.staging.yml"

if [ "$#" -ne 5 ] || [ "$5" != "RESTORE_STAGING" ]; then
    echo "Kullanım: bash restore.sh <.env.staging> <database.dump> <uploads.tar.gz> <manifest.sha256> RESTORE_STAGING" >&2
    echo "Bu işlem staging veritabanını ve yükleme alanını mevcut yedekle değiştirir." >&2
    exit 1
fi

env_file=$1
database_file=$2
uploads_file=$3
manifest_file=$4

for required_file in "$env_file" "$database_file" "$uploads_file" "$manifest_file"; do
    if [ ! -f "$required_file" ]; then
        echo "Dosya bulunamadı: $required_file" >&2
        exit 1
    fi
done

sh "$script_dir/validate-staging-env.sh" "$env_file"

if ! command -v docker >/dev/null 2>&1; then
    echo "Docker bulunamadı." >&2
    exit 1
fi

if ! command -v sha256sum >/dev/null 2>&1; then
    echo "sha256sum bulunamadı." >&2
    exit 1
fi

database_dir=$(CDPATH= cd -- "$(dirname -- "$database_file")" && pwd)
uploads_dir=$(CDPATH= cd -- "$(dirname -- "$uploads_file")" && pwd)
manifest_dir=$(CDPATH= cd -- "$(dirname -- "$manifest_file")" && pwd)
if [ "$database_dir" != "$manifest_dir" ] || [ "$uploads_dir" != "$manifest_dir" ]; then
    echo "Yedek dosyaları ve SHA-256 manifesti aynı dizinde olmalı." >&2
    exit 1
fi

database_file="$database_dir/$(basename -- "$database_file")"
uploads_file="$uploads_dir/$(basename -- "$uploads_file")"
manifest_file="$manifest_dir/$(basename -- "$manifest_file")"
verify_checksum() {
    checked_file=$1
    checked_name=$(basename -- "$checked_file")
    expected_hash=$(awk -v name="$checked_name" '$2 == name { print $1; exit }' "$manifest_file")
    actual_hash=$(sha256sum "$checked_file" | awk '{ print $1 }')
    if [ -z "$expected_hash" ] || [ "$expected_hash" != "$actual_hash" ]; then
        echo "SHA-256 doğrulaması başarısız: $checked_name" >&2
        exit 1
    fi
    echo "$checked_name: OK"
}
verify_checksum "$database_file"
verify_checksum "$uploads_file"

archive_list=$(mktemp)
cleanup() {
    rm -f -- "$archive_list"
}
trap cleanup EXIT HUP INT TERM

tar -tzf "$uploads_file" > "$archive_list"
if [ ! -s "$archive_list" ]; then
    echo "Yükleme arşivi boş." >&2
    exit 1
fi
if tar -tvzf "$uploads_file" | grep -Eq '^[lh]'; then
    echo "Yükleme arşivinde sembolik veya sabit bağlantı bulundu." >&2
    exit 1
fi
while IFS= read -r archive_entry; do
    case "$archive_entry" in
        uploads|uploads/*) ;;
        *)
            echo "Yükleme arşivinde beklenmeyen yol var: $archive_entry" >&2
            exit 1
            ;;
    esac
    case "/$archive_entry/" in
        */../*)
            echo "Yükleme arşivinde güvensiz yol var: $archive_entry" >&2
            exit 1
            ;;
    esac
done < "$archive_list"

docker compose --env-file "$env_file" -f "$compose_file" up -d postgres
if ! docker compose --env-file "$env_file" -f "$compose_file" exec -T postgres \
    pg_restore --list < "$database_file" >/dev/null; then
    echo "PostgreSQL dump yapısı doğrulanamadı; geri yükleme başlatılmadı." >&2
    exit 1
fi
echo "PostgreSQL dump yapısı doğrulandı."

pre_restore_dir="$script_dir/backups/pre-restore"
echo "Geri yükleme öncesi güvenlik yedeği alınıyor..."
sh "$script_dir/backup.sh" "$env_file" "$pre_restore_dir"

restart_stack() {
    docker compose --env-file "$env_file" -f "$compose_file" up -d >/dev/null 2>&1 || true
}
trap 'cleanup; restart_stack' EXIT HUP INT TERM

docker compose --env-file "$env_file" -f "$compose_file" stop edge frontend backend

docker compose --env-file "$env_file" -f "$compose_file" exec -T postgres \
    sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --no-privileges --single-transaction' \
    < "$database_file"

docker compose --env-file "$env_file" -f "$compose_file" run --rm --no-deps -T \
    --entrypoint sh backend \
    -c 'find /app/uploads -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + && tar -C /app -xzf -' \
    < "$uploads_file"

docker compose --env-file "$env_file" -f "$compose_file" up -d

trap - EXIT HUP INT TERM
cleanup
echo "Staging geri yükleme tamamlandı. Sağlık kontrollerini ve temel kullanıcı akışlarını doğrulayın."
