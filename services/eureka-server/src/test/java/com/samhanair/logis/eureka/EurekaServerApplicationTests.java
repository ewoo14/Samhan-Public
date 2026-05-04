package com.samhanair.logis.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test: verifies the Spring application context loads with the
 * {@code @EnableEurekaServer} bean graph wired up. Eureka client side is
 * disabled so the test does not attempt to register with itself or any peer.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
})
class EurekaServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
