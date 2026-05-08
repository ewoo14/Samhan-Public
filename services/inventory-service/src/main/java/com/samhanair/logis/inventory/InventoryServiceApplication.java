package com.samhanair.logis.inventory;

import com.samhanair.logis.common.audit.JpaAuditingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

/** Inventory Service entry point — Warehouse + Stock 도메인 (plan §3 first slice). */
@SpringBootApplication
@EnableDiscoveryClient
@Import(JpaAuditingConfig.class)
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
