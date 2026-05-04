# SamhanLogis — API Gateway image.
# Single-stage: build the jar with Gradle on the host, then COPY into a slim
# JRE image. Runs as non-root user `app`; wget is bundled for healthchecks.
FROM eclipse-temurin:17-jre-alpine

ARG JAR_FILE=build/libs/api-gateway.jar

RUN apk add --no-cache wget \
    && addgroup -S app \
    && adduser -S app -G app \
    && mkdir -p /app \
    && chown -R app:app /app

WORKDIR /app

COPY ${JAR_FILE} /app/app.jar
RUN chown app:app /app/app.jar

USER app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java","-jar","/app/app.jar"]
