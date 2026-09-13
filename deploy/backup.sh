#!/usr/bin/env sh
set -eu

umask 077

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
env_file=${1:-"$script_dir/.env.staging"}
backup_dir=${2:-"$script_dir/backups"}
compose_file="$script_dir/docker-compose.staging.yml"

if [ ! -f "$env_file" ]; then
    echo "Ortam dosyası bulunamadı: $env_file" >&2
    exit 1
fi

sh "$script_dir/validate-staging-env.sh" "$env_file"

if ! command -v docker >/dev/null 2>&1; then
    echo "Docker bulunamadı." >&2
    exit 1
fi

if ! command -v sha256sum >/dev/null 2>&1; then
    echo "sha256sum bulunamadı." >&2
    exit 1
fi

mkdir -p "$backup_dir"
backup_dir=$(CDPATH= cd -- "$backup_dir" && pwd)

case "$backup_dir" in
    /|/etc|/usr|/var|/home|/root)
        echo "Güvensiz yedek dizini reddedildi: $backup_dir" >&2
        exit 1
        ;;
esac

timestamp=$(date -u +%Y%m%dT%H%M%SZ)
database_file="$backup_dir/mealflex-staging-$timestamp.database.dump"
uploads_file="$backup_dir/mealflex-staging-$timestamp.uploads.tar.gz"
manifest_file="$backup_dir/mealflex-staging-$timestamp.sha256"
database_temp="$database_file.partial"
uploads_temp="$uploads_file.partial"

cleanup() {
    rm -f -- "$database_temp" "$uploads_temp"
}
restart_stack() {
    docker compose --env-file "$env_file" -f "$compose_file" up -d >/dev/null 2>&1 || true
}
trap 'cleanup; restart_stack' EXIT HUP INT TERM

docker compose --env-file "$env_file" -f "$compose_file" up -d postgres
attempt=0
until docker compose --env-file "$env_file" -f "$compose_file" exec -T postgres \
    sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 30 ]; then
        echo "PostgreSQL 60 saniye içinde hazır olmadı." >&2
        exit 1
    fi
    sleep 2
done
docker compose --env-file "$env_file" -f "$compose_file" stop edge frontend backend

docker compose --env-file "$env_file" -f "$compose_file" exec -T postgres \
    sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' \
    > "$database_temp"

docker compose --env-file "$env_file" -f "$compose_file" run --rm --no-deps -T \
    --entrypoint tar backend -C /app -czf - uploads \
    > "$uploads_temp"

mv -- "$database_temp" "$database_file"
mv -- "$uploads_temp" "$uploads_file"

(
    cd -- "$backup_dir"
    sha256sum "$(basename -- "$database_file")" "$(basename -- "$uploads_file")" \
        > "$(basename -- "$manifest_file")"
)

docker compose --env-file "$env_file" -f "$compose_file" up -d

trap - EXIT HUP INT TERM

echo "Yedek tamamlandı:"
echo "  $database_file"
echo "  $uploads_file"
echo "  $manifest_file"
