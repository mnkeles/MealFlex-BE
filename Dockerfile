# syntax=docker/dockerfile:1

FROM maven:3.9.15-eclipse-temurin-26 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system mealflex \
    && useradd --system --gid mealflex --home-dir /app --shell /usr/sbin/nologin mealflex

WORKDIR /app
COPY --from=build --chown=mealflex:mealflex /workspace/target/mealflex-backend-*.jar /app/app.jar
RUN mkdir -p /app/uploads && chown mealflex:mealflex /app/uploads

USER mealflex
EXPOSE 9090

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl --fail --silent http://127.0.0.1:9090/api/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
