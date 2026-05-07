package com.samhanair.logis.arologis.client;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * slip-service 호출 client — Phase 10 W10-1 arologis-service.
 *
 * <p>전자서명 통합은 W10-4 시점. 본 PR (W10-1) 은 client skeleton 만 보유 — 실 endpoint 호출은
 * W10-4 통합 PR 에서 구현.
 *
 * <p>예상 endpoint (W10-4):
 * <ul>
 *   <li>POST {@code /internal/slips/{slipId}/signatures} — 정차 완료 시 전자서명 imageRef 등록</li>
 * </ul>
 */
@Slf4j
@Component
public class SlipClient {

    private final RestClient.Builder builder;
    private final String baseUrl;
    private final String internalToken;
    private final boolean skeletonMode;

    public SlipClient(RestClient.Builder builder,
                      @Value("${samhan.slip-service.url:http://localhost:8084}") String baseUrl,
                      @Value("${app.security.internal.token:}") String internalToken,
                      @Value("${samhan.arologis.client.skeleton-mode:true}") boolean skeletonMode) {
        this.builder = builder;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.skeletonMode = skeletonMode;
    }

    /**
     * 전자서명 등록 — W10-4 통합 시점 활성. 본 PR (W10-1) 은 skeleton-mode 강제 + 항상 false.
     *
     * @param slipId 전표 UUID (정차의 parsedPartnerCode → slip-service slipId 매핑)
     * @param imageRef 이미지 reference (file-server 경로)
     * @return 성공 시 true. W10-1 단계 / skeleton-mode 시 항상 false.
     */
    public boolean registerSignature(UUID slipId, String imageRef) {
        if (skeletonMode) {
            log.debug("SlipClient.registerSignature skeleton-mode — slipId={} imageRef={} (W10-4 통합 시점 활성)",
                    slipId, imageRef);
            return false;
        }
        // W10-4 통합 시점 실 호출 구현 의무
        log.warn("SlipClient.registerSignature — W10-4 통합 시점에 구현 예정. 현재 호출은 무시.");
        return false;
    }
}
