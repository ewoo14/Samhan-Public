package com.samhanair.logis.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link EurekaServiceDiscoveryClient} 단위 테스트.
 *
 * <p>{@link EurekaClient} 를 mock 으로 격리하여 lookup → ServiceInstance 정규화,
 * UP filter, healthcheck 동작을 검증한다.
 */
class EurekaServiceDiscoveryClientTest {

    @Test
    void lookup_returns_empty_list_when_application_not_registered() {
        EurekaClient client = mock(EurekaClient.class);
        when(client.getApplication(eq("missing"))).thenReturn(null);

        var subject = new EurekaServiceDiscoveryClient(client);

        assertThat(subject.lookup("missing")).isEmpty();
        assertThat(subject.healthcheck("missing")).isFalse();
    }

    @Test
    void lookup_filters_to_up_instances_and_normalizes_metadata() {
        InstanceInfo up = mock(InstanceInfo.class);
        when(up.getStatus()).thenReturn(InstanceInfo.InstanceStatus.UP);
        when(up.getAppName()).thenReturn("product-service");
        when(up.getHostName()).thenReturn("product-1.local");
        when(up.getPort()).thenReturn(8084);
        when(up.isPortEnabled(InstanceInfo.PortType.SECURE)).thenReturn(false);
        when(up.getInstanceId()).thenReturn("product-1.local:product-service:8084");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("zone", "kr-central-1a");
        when(up.getMetadata()).thenReturn(metadata);

        InstanceInfo down = mock(InstanceInfo.class);
        when(down.getStatus()).thenReturn(InstanceInfo.InstanceStatus.DOWN);

        Application application = mock(Application.class);
        when(application.getInstances()).thenReturn(List.of(up, down));

        EurekaClient client = mock(EurekaClient.class);
        when(client.getApplication(eq("product-service"))).thenReturn(application);

        var subject = new EurekaServiceDiscoveryClient(client);

        List<ServiceInstance> result = subject.lookup("product-service");

        assertThat(result).hasSize(1);
        ServiceInstance instance = result.get(0);
        assertThat(instance.serviceName()).isEqualTo("product-service");
        assertThat(instance.host()).isEqualTo("product-1.local");
        assertThat(instance.port()).isEqualTo(8084);
        assertThat(instance.secure()).isFalse();
        assertThat(instance.metadata()).containsEntry("zone", "kr-central-1a");
        assertThat(instance.metadata()).containsEntry("instanceId", "product-1.local:product-service:8084");

        assertThat(subject.healthcheck("product-service")).isTrue();
    }

    @Test
    void lookup_uses_secure_port_when_secure_enabled() {
        InstanceInfo info = mock(InstanceInfo.class);
        when(info.getStatus()).thenReturn(InstanceInfo.InstanceStatus.UP);
        when(info.getAppName()).thenReturn("auth-service");
        when(info.getHostName()).thenReturn("auth-1.local");
        when(info.isPortEnabled(InstanceInfo.PortType.SECURE)).thenReturn(true);
        when(info.getSecurePort()).thenReturn(8443);
        when(info.getInstanceId()).thenReturn("auth-1.local:auth-service:8443");

        Application application = mock(Application.class);
        when(application.getInstances()).thenReturn(List.of(info));

        EurekaClient client = mock(EurekaClient.class);
        when(client.getApplication(eq("auth-service"))).thenReturn(application);

        var subject = new EurekaServiceDiscoveryClient(client);

        ServiceInstance instance = subject.lookup("auth-service").get(0);
        assertThat(instance.port()).isEqualTo(8443);
        assertThat(instance.secure()).isTrue();
    }

    @Test
    void register_and_deregister_are_no_op() {
        EurekaClient client = mock(EurekaClient.class);
        var subject = new EurekaServiceDiscoveryClient(client);

        // 예외가 발생하지 않으면 OK — Eureka framework 가 자동 처리, 본 호출은 호환 no-op.
        subject.register("product-service", "host", 8084);
        subject.deregister("product-service");
    }
}
