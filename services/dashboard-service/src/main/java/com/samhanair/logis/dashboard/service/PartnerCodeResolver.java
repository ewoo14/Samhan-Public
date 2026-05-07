package com.samhanair.logis.dashboard.service;

import com.samhanair.logis.dashboard.client.PartnerClient;
import com.samhanair.logis.dashboard.client.PartnerSummary;
import com.samhanair.logis.dashboard.config.CacheConfig;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * partnerCode → partnerId UUID resolve service — Phase 9 W4 후속 fix (QA Q-W4-2 채택).
 *
 * <p>UUID 비공개 가드 (memory feedback_uuid_no_user_visibility) 일관 — admin endpoint 의 입력
 * 파라미터에서 partnerId UUID 를 제거하고 partnerCode 를 받아 service 가 내부적으로 UUID 로 변환.
 *
 * <p>resolve 결과는 Caffeine cache ({@link CacheConfig#CACHE_PARTNER_RESOLVE}) 에 보관 — 동일
 * partnerCode 의 반복 호출 시 partner-service RPC 회피. cache eviction 정책은 CacheConfig 의
 * 단일 manager TTL 적용 (W4 기준 KPI TTL 과 공유).
 *
 * <p>skeleton-mode (= W4 default) 환경에서는 {@link PartnerClient#findByCode(String)} 가
 * Optional.empty 를 반환하므로 본 resolver 도 항상 Optional.empty 반환 — admin controller 는
 * partnerCode 입력 시 400 으로 응답하거나 partnerId null 로 처리 (사용자 노출 0 의 skeleton 의도).
 *
 * <p>Phase 10 cutover 시점 ({@code samhan.dashboard.client.skeleton-mode=false}) 에 partner-service
 * 응답 파싱이 활성되면서 본 resolver 가 실 UUID 를 반환.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerCodeResolver {

    private final PartnerClient partnerClient;

    /**
     * partnerCode 로 partnerId UUID 를 조회. cache hit 시 RPC 회피.
     *
     * <p>PR #94 W4 후속 fix (IT 1건 fail 정정) — Spring Cache 가 {@code Optional} 반환 타입을
     * 자동 unwrap 하여 {@code #result} SpEL 변수에는 unwrap 된 UUID (또는 null) 가 바인딩됨.
     * 따라서 {@code unless} 표현식에서 {@code #result.isPresent()} 호출 시 UUID 에 해당 메서드가
     * 없어 {@code SpelEvaluationException} 발생하고 controller 호출이 500 으로 실패한다.
     * unwrap 된 값이 null (= 원래 Optional.empty) 인 케이스만 캐시 회피하면 충분.
     *
     * @param partnerCode 거래처 코드 (사용자 노출 식별자, nullable / blank 시 empty)
     * @return partnerId UUID (있으면 Optional.of, skeleton-mode / 미존재 / 호출 실패 시 empty)
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_PARTNER_RESOLVE,
            key = "#partnerCode == null ? '__null__' : #partnerCode",
            unless = "#result == null")
    public Optional<UUID> resolve(String partnerCode) {
        if (partnerCode == null || partnerCode.isBlank()) {
            return Optional.empty();
        }
        Optional<PartnerSummary> summary = partnerClient.findByCode(partnerCode);
        if (summary.isEmpty()) {
            log.debug("PartnerCodeResolver — partnerCode={} 미존재 또는 skeleton-mode", partnerCode);
            return Optional.empty();
        }
        return Optional.ofNullable(summary.get().partnerId());
    }
}
