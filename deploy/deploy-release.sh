#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
env_file=${1:-"$script_dir/.env.staging"}
release_version=${2:-}
compose_file="$script_dir/docker-compose.staging.yml"

read_value() {
    key=$1
    sed -n "s/^${key}=//p" "$env_file" | tail -n 1
}

if [ "${#release_version}" -gt 64 ] ||
   ! printf '%s' "$release_version" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9._-]*$'; then
    echo "Kullanım: sh deploy-release.sh <.env.staging> <sürüm>" >&2
    exit 1
fi

sh "$script_dir/validate-staging-env.sh" "$env_file"
previous_version=$(read_value APP_VERSION)
if ! command -v docker >/dev/null 2>&1; then
    echo "Docker bulunamadı." >&2
    exit 1
fi
if ! command -v git >/dev/null 2>&1; then
    echo "Git bulunamadı; doğrulanmış commit'ten yayın yapılamaz." >&2
    exit 1
fi

backend_repo=$(CDPATH= cd -- "$script_dir/.." && pwd)
frontend_repo=$(CDPATH= cd -- "$script_dir/../../MealFlex-FE" && pwd)
for repository in "$backend_repo" "$frontend_repo"; do
    if [ "$(git -C "$repository" rev-parse --is-inside-work-tree 2>/dev/null || true)" != "true" ]; then
        echo "Git reposu bulunamadı: $repository" >&2
        exit 1
    fi
    if [ -n "$(git -C "$repository" status --porcelain)" ]; then
        echo "Kirli çalışma ağacından yayın reddedildi: $repository" >&2
        exit 1
    fi
done

if docker image inspect "mealflex-backend:$release_version" >/dev/null 2>&1 ||
   docker image inspect "mealflex-frontend:$release_version" >/dev/null 2>&1; then
    echo "Daha önce oluşturulmuş imaj etiketi yeniden kullanılamaz: $release_version." >&2
    exit 1
fi

rollback_available=0
if [ "$previous_version" != "$release_version" ] &&
   docker image inspect "mealflex-backend:$previous_version" >/dev/null 2>&1 &&
   docker image inspect "mealflex-frontend:$previous_version" >/dev/null 2>&1; then
    rollback_available=1
fi

echo "Backend commit: $(git -C "$backend_repo" rev-parse --short=12 HEAD)"
echo "Frontend commit: $(git -C "$frontend_repo" rev-parse --short=12 HEAD)"

postgres_id=$(docker compose --env-file "$env_file" -f "$compose_file" ps -q postgres 2>/dev/null || true)
if [ -n "$postgres_id" ]; then
    echo "Yayın öncesi güvenlik yedeği alınıyor..."
    sh "$script_dir/backup.sh" "$env_file" "$script_dir/backups/pre-release"
else
    echo "İlk kurulum: mevcut staging verisi olmadığı için yayın öncesi yedek atlandı."
fi

export APP_VERSION=$release_version
docker compose --env-file "$env_file" -f "$compose_file" build --pull backend frontend
deployment_succeeded=1
if ! docker compose --env-file "$env_file" -f "$compose_file" up -d --wait --wait-timeout 180; then
    echo "Yeni sürüm konteynerleri zamanında healthy olmadı." >&2
    deployment_succeeded=0
elif ! sh "$script_dir/verify-deployment.sh" "$env_file"; then
    deployment_succeeded=0
fi

if [ "$deployment_succeeded" -eq 0 ]; then
    echo "Yeni sürüm doğrulanamadı. APP_VERSION kalıcılaştırılmadı." >&2
    if [ "$rollback_available" -eq 1 ]; then
        echo "Önceki uygulama sürümüne otomatik dönüş deneniyor: $previous_version" >&2
        export APP_VERSION=$previous_version
        if docker compose --env-file "$env_file" -f "$compose_file" up -d --no-build --wait --wait-timeout 180 &&
           sh "$script_dir/verify-deployment.sh" "$env_file"; then
            echo "Önceki uygulama sürümüne dönüldü: $previous_version" >&2
        else
            echo "CRITICAL Otomatik dönüş doğrulanamadı; operatör müdahalesi gerekiyor." >&2
        fi
    else
        echo "Önceki sürüm imajları bulunamadı; otomatik dönüş yapılamadı." >&2
    fi
    exit 1
fi

env_temp=$(mktemp "${env_file}.XXXXXX")
cleanup() {
    rm -f -- "$env_temp"
}
trap cleanup EXIT HUP INT TERM
awk -v version="$release_version" '
    BEGIN { updated = 0 }
    /^APP_VERSION=/ { print "APP_VERSION=" version; updated = 1; next }
    { print }
    END { if (!updated) print "APP_VERSION=" version }
' "$env_file" > "$env_temp"
chmod 600 "$env_temp"
mv -- "$env_temp" "$env_file"
trap - EXIT HUP INT TERM

echo "Staging sürümü yayınlandı ve kalıcılaştırıldı: $release_version"
