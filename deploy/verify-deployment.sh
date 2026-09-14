#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
env_file=${1:-"$script_dir/.env.staging"}
compose_file="$script_dir/docker-compose.staging.yml"

sh "$script_dir/validate-staging-env.sh" "$env_file"

if ! command -v docker >/dev/null 2>&1 || ! command -v curl >/dev/null 2>&1; then
    echo "Docker ve curl kurulu olmalı." >&2
    exit 1
fi

read_value() {
    key=$1
    sed -n "s/^${key}=//p" "$env_file" | tail -n 1
}

public_host=$(read_value PUBLIC_HOST)
public_base_url=$(read_value PUBLIC_BASE_URL)

docker compose --env-file "$env_file" -f "$compose_file" config --quiet

for service in postgres backend frontend edge; do
    container_id=$(docker compose --env-file "$env_file" -f "$compose_file" ps -q "$service")
    if [ -z "$container_id" ]; then
        echo "$service konteyneri bulunamadı." >&2
        exit 1
    fi
    state=$(docker inspect --format '{{.State.Status}}' "$container_id")
    health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id")
    if [ "$state" != "running" ] || [ "$health" != "healthy" ]; then
        echo "$service hazır değil (state=$state, health=$health)." >&2
        exit 1
    fi
    echo "PASS $service çalışıyor ve healthy"
done

if ! http_status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
    --max-time 20 "http://$public_host/"); then
    echo "HTTP yönlendirme kontrolü alan adına bağlanamadı." >&2
    exit 1
fi
case "$http_status" in
    301|302|307|308) echo "PASS HTTP, HTTPS'e yönleniyor ($http_status)" ;;
    *)
        echo "HTTP yönlendirmesi bekleniyordu; $http_status alındı." >&2
        exit 1
        ;;
esac

if ! health_body=$(curl --fail --silent --show-error --max-time 20 \
    "$public_base_url/api/actuator/health/readiness"); then
    echo "HTTPS readiness endpointine bağlanılamadı." >&2
    exit 1
fi
if ! printf '%s' "$health_body" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    echo "Readiness endpointi UP döndürmedi." >&2
    exit 1
fi
echo "PASS HTTPS readiness UP"

if ! headers=$(curl --fail --silent --show-error --head --max-time 20 "$public_base_url/"); then
    echo "HTTPS başlık kontrolü tamamlanamadı." >&2
    exit 1
fi
if ! printf '%s' "$headers" | grep -Eiq '^strict-transport-security:'; then
    echo "Strict-Transport-Security başlığı bulunamadı." >&2
    exit 1
fi
echo "PASS HSTS başlığı mevcut"

if ! swagger_status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
    --max-time 20 "$public_base_url/api/swagger-ui.html"); then
    echo "Swagger kapalılık kontrolü alan adına bağlanamadı." >&2
    exit 1
fi
if [ "$swagger_status" != "404" ]; then
    echo "Production Swagger endpointi 404 dönmeli; $swagger_status alındı." >&2
    exit 1
fi
echo "PASS Swagger dış erişime kapalı"

if ! protected_status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
    --max-time 20 "$public_base_url/api/v1/subscriptions"); then
    echo "Anonim API kontrolü alan adına bağlanamadı." >&2
    exit 1
fi
if [ "$protected_status" != "401" ]; then
    echo "Anonim korumalı API isteği 401 dönmeli; $protected_status alındı." >&2
    exit 1
fi
echo "PASS anonim korumalı API erişimi reddediliyor"

failed_migrations=$(docker compose --env-file "$env_file" -f "$compose_file" exec -T postgres \
    sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select count(*) from flyway_schema_history where not success"')
if [ "$failed_migrations" != "0" ]; then
    echo "Flyway geçmişinde başarısız migration var: $failed_migrations" >&2
    exit 1
fi
echo "PASS Flyway geçmişinde başarısız migration yok"

echo "Staging yayın doğrulaması başarıyla tamamlandı."
