package com.samhanair.logis.arologis.service;

import com.samhanair.logis.arologis.client.SlipClient;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 정차 partnerCode → slip-service slipId 매핑 — Phase 10 W10-4 (PR #99) 신규.
 *
 * <p>arologis-service 의 SignatureService 가 driver-app 정차 완료 시 slip-service 의 인수자/기사
 * 서명을 갱신하기 위해 본 resolver 로 slipId 를 얻는다.
 *
 * <p>W10-4 종합 TM (BE-1 채택) — slip-service 측 신규 endpoint
 * {@code GET /internal/slips/by-partner-code/{code}/recent} 가 partnerCode 를 직접 받아 자체
 * PartnerInternalClient 로 partner-service 에서 partnerId resolve 후 lookup 한다. 따라서 arologis 측
 * SlipResolver 는 partnerCode 를 그대로 SlipClient.findRecentSlipIdByPartnerCode 에 위임하면 된다.
 *
 * <p>매칭 실패 시 Optional.empty — SignatureService 가 graceful fallback (자체 signatures INSERT 만,
 * slip-service 호출 skip + warn log).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlipResolver {

    private final SlipClient slipClient;

    /**
     * 카톡 슬립번호 (kakaoSeq) 로 slipId 매핑 — arologis 정차 완료 시 호출.
     *
     * <p>W10-4 종합 TM (BE-1 채택) — slip-service 신규 by-partner-code endpoint 위임. slip-service
     * 가 자체 partner-service lookup 으로 partnerId resolve 후 자체 slips lookup. graceful empty
     * 패턴 — 매핑 실패 시 200 + data=null → SlipClient 가 empty Optional 반환.
     *
     * <p>PR-E 진입 전 선행 R2 — 파라미터 명칭 분리 (parsedPartnerCode → parsedKakaoSeq). 본 메서드의
     * 의미는 변경 없음 — slip-service 측 endpoint 가 카톡 슬립번호 String 형태를 그대로 받아 자체 매핑
     * (slip-service 측 리팩터링은 PR-E1 별도 진행).
     *
     * @param parsedKakaoSeq 카톡 슬립번호 (Long, 사용자 노출 식별자, 예: 214)
     * @return 매칭된 slipId Optional. 매칭 실패 시 empty.
     */
    public Optional<UUID> resolveByKakaoSeq(Long parsedKakaoSeq) {
        if (parsedKakaoSeq == null) {
            return Optional.empty();
        }
        Optional<UUID> slipIdOpt = slipClient.findRecentSlipIdByPartnerCode(String.valueOf(parsedKakaoSeq));
        if (slipIdOpt.isPresent()) {
            log.info("SlipResolver — kakaoSeq={} → slipId={} 매핑 성공 (slip-service by-partner-code)",
                    parsedKakaoSeq, slipIdOpt.get());
        } else {
            log.debug("SlipResolver — kakaoSeq={} 매핑 실패 (slip-service 200 + data=null 또는 skeleton-mode)",
                    parsedKakaoSeq);
        }
        return slipIdOpt;
    }

    /**
     * partnerId UUID 직접 lookup — admin endpoint 또는 후속 cycle 매핑 cache 보유 시 사용.
     *
     * @param partnerId 거래처 UUID
     * @return 매칭된 slipId Optional
     */
    public Optional<UUID> resolveByPartnerId(UUID partnerId) {
        if (partnerId == null) {
            return Optional.empty();
        }
        return slipClient.findRecentSlipIdByPartner(partnerId);
    }
}
