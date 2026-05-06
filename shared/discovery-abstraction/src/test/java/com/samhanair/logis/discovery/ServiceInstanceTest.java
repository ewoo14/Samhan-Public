package com.samhanair.logis.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ServiceInstance} record 검증.
 */
class ServiceInstanceTest {

    @Test
    void rejects_null_service_name() {
        assertThatThrownBy(() -> new ServiceInstance(null, "host", 8080, false, Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_null_host() {
        assertThatThrownBy(() -> new ServiceInstance("svc", null, 8080, false, Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_port_out_of_range() {
        assertThatThrownBy(() -> new ServiceInstance("svc", "host", 0, false, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ServiceInstance("svc", "host", 65536, false, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_metadata_normalized_to_empty_map() {
        ServiceInstance instance = new ServiceInstance("svc", "host", 8080, false, null);
        assertThat(instance.metadata()).isEmpty();
    }

    @Test
    void metadata_is_unmodifiable() {
        Map<String, String> source = new HashMap<>();
        source.put("zone", "kr-central-1a");
        ServiceInstance instance = new ServiceInstance("svc", "host", 8080, false, source);
        assertThatThrownBy(() -> instance.metadata().put("region", "kr"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
