package com.samhanair.logis.dashboard.service;

import com.samhanair.logis.dashboard.client.PartnerClient;
import com.samhanair.logis.dashboard.client.PartnerSummary;
import com.samhanair.logis.dashboard.config.CacheConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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
public class PartnerCodeResolver {

    private final PartnerClient partnerClient;
    private final CacheManager cacheManager;

    /**
     * Constructor — Spring 이 PartnerClient + CacheManager 자동 주입. cacheManager 는 W5 신규
     * {@link #resolveAll(List)} 의 cache hit/miss 분리용 (단건 {@link #resolve(String)} 은 기존
     * {@code @Cacheable} 패턴 유지).
     */
    public PartnerCodeResolver(PartnerClient partnerClient, CacheManager cacheManager) {
        this.partnerClient = partnerClient;
        this.cacheManager = cacheManager;
    }

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

    /**
     * partnerCode N건 bulk resolve — Phase 9 W5 신규 (D-P9-16, BE 의견 3 채택).
     *
     * <p>fan-out N회 직렬 RPC 회피용. 처리 흐름:
     * <ol>
     *   <li>입력 코드를 cache hit / miss 로 분리 (Caffeine 캐시 직접 조회).</li>
     *   <li>miss 코드만 {@link PartnerClient#findByCodes(List)} 1회 호출 → bulk RPC.</li>
     *   <li>응답 row 를 cache 에 적재 (단건 {@link #resolve(String)} 와 동일 cache name 공유).</li>
     *   <li>hit + 신규 응답 합쳐 partnerCode → UUID Map 반환.</li>
     * </ol>
     *
     * <p>skeleton-mode 환경에서는 client 가 빈 리스트를 반환하므로 본 메서드도 hit 결과만 반환 (실 운영
     * 진입 시점 cache 가 비어있으면 빈 Map). 미존재 partnerCode 는 결과 Map 에 누락 — 호출 측이
     * Map containsKey 로 분기.
     *
     * @param partnerCodes 조회할 partnerCode 모음 (null/empty → 빈 Map)
     * @return partnerCode → UUID Map (매칭된 항목만 포함)
     */
    public Map<String, UUID> resolveAll(List<String> partnerCodes) {
        if (partnerCodes == null || partnerCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_PARTNER_RESOLVE);
        Map<String, UUID> result = new HashMap<>();
        List<String> miss = new ArrayList<>();

        for (String code : partnerCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            UUID hit = readCacheUuid(cache, code);
            if (hit != null) {
                result.put(code, hit);
            } else {
                miss.add(code);
            }
        }

        if (miss.isEmpty()) {
            return result;
        }

        List<PartnerSummary> summaries = partnerClient.findByCodes(miss);
        for (PartnerSummary s : summaries) {
            if (s == null || s.partnerCode() == null || s.partnerId() == null) {
                continue;
            }
            result.put(s.partnerCode(), s.partnerId());
            if (cache != null) {
                // 단건 resolve 와 동일하게 Optional<UUID> 형태로 적재 — Spring Cache 가 단건 SpEL
                // {@code unless = "#result == null"} 로 unwrap UUID null 만 회피. 본 직접 적재는
                // 사용 시 wrap 일관성을 위해 Optional 로 감싸 둔다.
                cache.put(s.partnerCode(), Optional.of(s.partnerId()));
            }
        }
        return result;
    }

    /**
     * cache 단건 read — Spring Cache 가 단건 {@link #resolve(String)} 진입 시 unwrap 한 UUID 또는
     * Optional<UUID> 를 보유할 수 있다. 두 형태 모두 안전하게 UUID 로 정규화.
     */
    private UUID readCacheUuid(Cache cache, String code) {
        if (cache == null) {
            return null;
        }
        Cache.ValueWrapper wrapper = cache.get(code);
        if (wrapper == null) {
            return null;
        }
        Object value = wrapper.get();
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }
}
