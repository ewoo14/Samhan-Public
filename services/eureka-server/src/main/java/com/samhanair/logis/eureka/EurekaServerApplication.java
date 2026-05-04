package com.samhanair.logis.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka discovery server entry point.
 *
 * <p>All SamhanLogis Phase 1 services register here for runtime discovery.
 * Activate the {@code prod-peer1} or {@code prod-peer2} profile for HA peer
 * mode; the default profile is suitable for local development only.</p>
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
