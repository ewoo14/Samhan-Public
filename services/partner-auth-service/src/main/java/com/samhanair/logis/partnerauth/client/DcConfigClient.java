package com.samhanair.logis.partnerauth.client;

import com.samhanair.logis.partnerauth.config.DcConfigClientProperties;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * dc-config-service (M3) internal RPC 클라이언트.
 *
 * <p><b>현 PR (M2 W2) 단계:</b> 인터페이스 + stub 응답 (M3 구현 전).
 * W3 단계에서 Eureka {@code lb://dc-config-service} + 실제 endpoint 연동 예정.
 *
 * <p>IT 에서는 본 클라이언트를 {@code @MockBean} 으로 격리한다
 * (memory feedback_it_mockbean_external_clients.md).
 */
@Component
public class DcConfigClient {

    private static final Logger log = LoggerFactory.getLogger(DcConfigClient.class);

    private final RestClient restClient;
    private final DcConfigClientProperties properties;

    public DcConfigClient(DcConfigClientProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getUrl())
                .build();
    }

    /**
     * 거래처(파트너) 마스터 조회 — bizNo 기준.
     *
     * @return 존재하지 않으면 {@link Optional#empty()} (NOT_FOUND_SYSTEM 결정 근거)
     */
    public Optional<PartnerConfigDto> findByBizNo(String bizNo) {
        // W3 정식 연동 전까지는 항상 empty (M3 미가용 가정).
        // IT 에서는 @MockBean 으로 override.
        log.debug("DcConfigClient.findByBizNo({}) — stub returning empty (W3 정식 연동 예정)", bizNo);
        return Optional.empty();
    }

    public String baseUrl() {
        return properties.getUrl();
    }
}
