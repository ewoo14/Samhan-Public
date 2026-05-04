package com.samhanair.logis.log;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Logging Service entrypoint (Phase 1, project plan §3.7).
 *
 * Consumes audit log events from RabbitMQ and persists them into
 * Elasticsearch. REST search endpoints are auth-protected at the gateway
 * (MASTER / MANAGER) — this service trusts upstream and does not re-check.
 */
@SpringBootApplication
public class LoggingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoggingServiceApplication.class, args);
    }
}
