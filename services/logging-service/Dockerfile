# SamhanLogis — logging-service container image
#
# Single-stage runtime on Eclipse Temurin 17 JRE Alpine.
# `curl` is added so the container HEALTHCHECK can hit the actuator
# endpoint without depending on the JRE having any HTTP client on $PATH.
FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S samhan && adduser -S samhan -G samhan

ARG JAR_FILE=build/libs/logging-service.jar
WORKDIR /app
COPY ${JAR_FILE} /app/logging-service.jar

USER samhan
EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/logging-service.jar"]
