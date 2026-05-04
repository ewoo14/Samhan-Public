# SamhanLogis — Auth Service image.
# Single-stage: build the jar with Gradle on the host, then COPY into a slim
# JRE image. Runs as non-root user `app`; curl is bundled for healthchecks.
FROM eclipse-temurin:17-jre-alpine

ARG JAR_FILE=build/libs/auth-service.jar

RUN apk add --no-cache curl \
    && addgroup -S app \
    && adduser -S app -G app \
    && mkdir -p /app \
    && chown -R app:app /app

WORKDIR /app

COPY ${JAR_FILE} /app/app.jar
RUN chown app:app /app/app.jar

USER app

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java","-jar","/app/app.jar"]
