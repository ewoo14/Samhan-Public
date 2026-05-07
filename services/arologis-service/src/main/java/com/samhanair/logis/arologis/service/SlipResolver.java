package com.samhanair.logis.arologis.service;

import com.samhanair.logis.arologis.client.PartnerClient;
import com.samhanair.logis.arologis.client.PartnerClient.PartnerSummary;
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
 * <p>2단계 lookup:
 * <ol>
 *   <li>partnerCode (Long, 카톡 파싱 결과 "(에스엠하나공조-214)" 의 214) → partner-service
 *       PartnerClient.findByCode → partnerId (UUID, partner-service 응답).
 *       <strong>중요</strong>: 현 단계 PartnerClient 는 partnerCode 만 응답에 포함하고 partnerId UUID
 *       는 미노출 (UUID 비공개 가드). 따라서 partner-service 측 W5 endpoint 가 UUID 없이도 응답
 *       가능한 형태이며, slip-service 의 partnerId 매핑은 별도 hook 필요.
 *       <p>본 PR 의 단순화 (spec ⚠️ fallback): partnerCode 자체를 slipId 매핑에 직접 사용하지 않고,
 *       PartnerSummary.partnerCode 가 존재하면 "매칭 가능" 으로만 판단. partnerId 변환은 slip-service
 *       /by-partner endpoint 가 받는 인자가 partnerId UUID 라 partner-service 가 UUID 노출하지 않는
 *       이상 직접 매핑이 어려움.
 *       <p>→ 따라서 본 resolver 는 spec 에 명시된 fallback "parsedPartnerCode 만 사용 + slip-service
 *       측 lookup" 패턴 적용 — slip-service 가 partnerCode (Long) 를 받는 endpoint 를 추가하거나,
 *       arologis 가 자체 매핑 캐시를 가지는 등 후속 cycle 결정 사항. 본 PR scope 내에서는 partnerId
 *       UUID 를 외부 입력 (예: dispatch 생성 시 admin 이 직접 지정) 으로 전달받은 경우만 matched.</li>
 *   <li>partnerId (UUID) → SlipClient.findRecentSlipIdByPartner → slipId.</li>
 * </ol>
 *
 * <p>매칭 실패 시 Optional.empty — SignatureService 가 graceful fallback (자체 signatures INSERT 만,
 * slip-service 호출 skip + warn log).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlipResolver {

    private final PartnerClient partnerClient;
    private final SlipClient slipClient;

    /**
     * partnerCode 로 slipId 매핑 — arologis 정차 완료 시 호출.
     *
     * <p>현 단계: partnerId UUID 직접 lookup 만 지원 (resolveByPartnerId). partnerCode (Long, 카톡
     * 파싱 결과) 는 검증만 (PartnerSummary 존재 여부) — slip-service /by-partner-code endpoint
     * 부재로 fallback. 후속 cycle 에서 slip-service 측 partnerCode 직접 lookup endpoint 추가하거나
     * partner-service 측 partnerCode → partnerId UUID 매핑 (UUID 비공개 가드 완화) 결정 필요.
     *
     * @param parsedPartnerCode 카톡 파싱 partnerCode (Long, 사용자 노출 식별자)
     * @return 매칭된 slipId Optional. 매칭 실패 시 empty.
     */
    public Optional<UUID> resolveByPartnerCode(Long parsedPartnerCode) {
        if (parsedPartnerCode == null) {
            return Optional.empty();
        }
        // 1단계: partner-service 검증 — partnerCode 가 등록된 거래처인지 확인.
        Optional<PartnerSummary> partnerOpt = partnerClient.findByCode(String.valueOf(parsedPartnerCode));
        if (partnerOpt.isEmpty()) {
            log.debug("SlipResolver — partnerCode={} 미등록 (PartnerClient.findByCode empty)",
                    parsedPartnerCode);
            return Optional.empty();
        }
        // 2단계: partnerId UUID 가 PartnerSummary 에 노출되지 않으므로 slip-service 직접 매핑 불가.
        // 본 PR 의 fallback: 매칭 가능성만 확인하고 empty 반환 — SignatureService 가 자체 INSERT
        // 후 graceful skip log.
        log.info("SlipResolver — partnerCode={} 매칭 가능 (partner-service 검증 OK), slipId 매핑은 후속 cycle (UUID 비공개 가드)",
                parsedPartnerCode);
        return Optional.empty();
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
