package com.samhanair.logis.discovery;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@link AwsCloudMapServiceDiscoveryClient} placeholder 검증.
 *
 * <p>모든 메서드가 {@link UnsupportedOperationException} 을 throw 하고 메시지에
 * "Phase 10 cutover 시점 구현" 표지가 포함되어야 한다.
 */
class AwsCloudMapServiceDiscoveryClientTest {

    private final AwsCloudMapServiceDiscoveryClient subject = new AwsCloudMapServiceDiscoveryClient();

    @Test
    void register_throws_unsupported() {
        assertThatThrownBy(() -> subject.register("svc", "host", 8080))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Phase 10 cutover 시점 구현");
    }

    @Test
    void deregister_throws_unsupported() {
        assertThatThrownBy(() -> subject.deregister("svc"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Phase 10 cutover 시점 구현");
    }

    @Test
    void lookup_throws_unsupported() {
        assertThatThrownBy(() -> subject.lookup("svc"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Phase 10 cutover 시점 구현");
    }

    @Test
    void healthcheck_throws_unsupported() {
        assertThatThrownBy(() -> subject.healthcheck("svc"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Phase 10 cutover 시점 구현");
    }
}
