package com.samhanair.logis.user;

import com.samhanair.logis.common.audit.JpaAuditingConfig;
import com.samhanair.logis.user.config.InternalAuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/** User Service entry point — Employee + Department aggregate (plan §3.4). */
@SpringBootApplication
@Import(JpaAuditingConfig.class)
@EnableConfigurationProperties(InternalAuthProperties.class)
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
