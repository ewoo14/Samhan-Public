package com.samhanair.logis.partnerorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.partnerorder.client.GoogleSheetsClient;
import com.samhanair.logis.partnerorder.client.GoogleSheetsClient.ValueRenderMode;
import com.samhanair.logis.partnerorder.domain.BootstrapCacheConfig;
import com.samhanair.logis.partnerorder.repository.BootstrapCacheConfigRepository;
import com.samhanair.logis.partnerorder.web.dto.BootstrapResponse;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 16종 bootstrap prefetch 서비스 (legacy index.html 1230~1244 + Code.js doGet 4~23 대체).
 *
 * <p><b>PR-D Part 1 보강</b>: 부팅 시 16 cache key 별 시트 직접 read 우선, 실패 시 V2 seed
 * fallback. Samhan Public 자체 service 안에서 시트 read — 외부 시스템 호출 X
 * (legacy estimate-app 패턴 보존). 시트 read 결과는 in-memory 로 보관 ({@link #sheetCache});
 * {@link Cacheable} 의 spring cache 와 별도 경로 — 시트 prefetch 가 갱신될 때마다 evict.
 *
 * <p>{@link Cacheable} 로 in-memory 캐시 — 카탈로그 변경 시 admin endpoint 가 evict.
 * config 키는 DC 9키 ({@code homeDiscount=0.45} 등) 가 제거된 client-safe 사본만 보관 (M3 가드 일관).
 *
 * <p>16 cache key (legacy 와 동일):
 * <pre>
 *   homemulti, singleSets, singleParts, homeDefaults, singleDefaults, singleMatPrices,
 *   commercialMulti, commercialParts, oldProducts,
 *   homeInc, commInc, singleInc, singlePartsInc,
 *   specDetailMap, config, logoData
 * </pre>
 *
 * <p><b>시트 매핑</b>: {@code app.bootstrap.range-map.<cacheKey>} 에 정의된 A1 range 가 있는
 * 키만 시트 read 시도. 매핑 없는 키는 V2 seed 만 사용. 시트 결과는 raw 2D ({@code List<List<Object>>})
 * 그대로 응답에 노출 — FE 가 legacy Apps Script 와 동일하게 row 배열로 처리.
 */
@Service
@RequiredArgsConstructor
public class BootstrapService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);

    /** 16종 cacheKey 목록 (FE 응답 키 순서 보존). */
    public static final List<String> CACHE_KEYS = List.of(
            "homemulti",
            "singleSets",
            "singleParts",
            "homeDefaults",
            "singleDefaults",
            "singleMatPrices",
            "commercialMulti",
            "commercialParts",
            "oldProducts",
            "homeInc",
            "commInc",
            "singleInc",
            "singlePartsInc",
            "specDetailMap",
            "config",
            "logoData");

    /**
     * config 키에서 제거되어야 할 DC 9키 (legacy CFG_RAW). client 응답 노출 금지.
     * M3 가드 일관 — DC 정보는 server-side priceVat 계산용 (M3 dc-config-service 직접 조회).
     */
    public static final Set<String> DC_SECRET_KEYS = Set.of(
            "homeDiscount",
            "commDiscount",
            "singleDiscount",
            "homePartsDiscount",
            "commPartsDiscount",
            "singlePartsDiscount",
            "oldDiscount",
            "incDiscount",
            "specDiscount");

    private final BootstrapCacheConfigRepository cacheRepository;
    private final ObjectMapper objectMapper;
    private final GoogleSheetsClient sheetsClient;

    @Value("${app.bootstrap.sheet-id:1RJqO3jT-yJTi3NDBhL60o_cZWlVETGTU7UlvIKXuVNQ}")
    private String bootstrapSheetId;

    /**
     * 시트 prefetch 활성 토글. local profile / 테스트에서 false 로 차단 가능.
     * default true — 운영에서는 부팅 시 자동 prefetch.
     */
    @Value("${app.bootstrap.sheet-prefetch-enabled:true}")
    private boolean sheetPrefetchEnabled;

    /**
     * cacheKey → 시트 A1 range 매핑. 매핑 없는 키는 시트 read 생략 후 V2 seed 만 사용.
     * application.yml {@code app.bootstrap.range-map} 으로 override.
     */
    @Value("#{${app.bootstrap.range-map:{:}}}")
    private Map<String, String> rangeMap;

    /** 시트 read 결과 캐시 (key=cacheKey, value=시트 raw payload). 부팅/admin trigger 시 갱신. */
    private final Map<String, Object> sheetCache = new ConcurrentHashMap<>();

    /**
     * 부팅 시 16 cache key prefetch — 시트 read 우선, 실패 시 V2 seed fallback.
     * Service Account JSON 부재 등으로 fail 해도 catch + log (부팅 차단 X).
     */
    @PostConstruct
    public void prefetch() {
        if (!sheetPrefetchEnabled) {
            log.info("[BootstrapService] 시트 prefetch 비활성 (app.bootstrap.sheet-prefetch-enabled=false) — V2 seed fallback only");
            return;
        }
        Map<String, String> effectiveRangeMap = rangeMap == null ? Map.of() : rangeMap;
        if (effectiveRangeMap.isEmpty()) {
            log.info("[BootstrapService] range-map 미설정 — 시트 prefetch skip (V2 seed only)");
            return;
        }
        log.info("[BootstrapService] 부팅 prefetch 시작: sheetId={}, mapping={}",
                bootstrapSheetId, effectiveRangeMap.keySet());
        int succeeded = 0;
        int failed = 0;
        for (String cacheKey : CACHE_KEYS) {
            String range = effectiveRangeMap.get(cacheKey);
            if (range == null || range.isBlank()) {
                continue;
            }
            try {
                List<List<Object>> rows = sheetsClient.readSheet(
                        bootstrapSheetId, range, ValueRenderMode.FORMATTED);
                sheetCache.put(cacheKey, rows);
                succeeded++;
                log.debug("[BootstrapService] 시트 prefetch 성공: key={}, rows={}",
                        cacheKey, rows == null ? 0 : rows.size());
            } catch (Exception e) {
                failed++;
                log.warn("[BootstrapService] 시트 prefetch 실패 (V2 seed fallback): key={}, range={}, err={}",
                        cacheKey, range, e.getMessage());
            }
        }
        log.info("[BootstrapService] 부팅 prefetch 완료: succeeded={}, failed={}, fallback={}",
                succeeded, failed, CACHE_KEYS.size() - succeeded);
    }

    /**
     * 16종 bootstrap 응답 — 시트 prefetch 우선, 부재 시 V2 seed fallback.
     * config 키는 DC 9키 제거 후 응답.
     *
     * @return BootstrapResponse — payloads Map (16개 cacheKey → 객체)
     */
    @Cacheable("bootstrap")
    @Transactional(readOnly = true)
    public BootstrapResponse fetch() {
        Map<String, Object> payloads = new LinkedHashMap<>();
        Map<String, BootstrapCacheConfig> rowsByKey = new HashMap<>();
        cacheRepository.findAllByOrderByCacheKeyAsc()
                .forEach(row -> rowsByKey.put(row.getCacheKey(), row));

        for (String key : CACHE_KEYS) {
            // 1) 시트 prefetch 결과 우선
            Object sheetPayload = sheetCache.get(key);
            if (sheetPayload != null) {
                payloads.put(key, applyConfigGuard(key, sheetPayload));
                continue;
            }
            // 2) V2 seed fallback
            BootstrapCacheConfig row = rowsByKey.get(key);
            if (row == null) {
                // legacy graceful fallback — 빈 객체
                payloads.put(key, "config".equals(key) ? Map.of() : List.of());
                continue;
            }
            Object parsed = parsePayload(row.getPayloadJson());
            payloads.put(key, applyConfigGuard(key, parsed));
        }
        return new BootstrapResponse(payloads);
    }

    /** config 키에 한해 DC 9키 strip — sheet/seed 양쪽 동일 가드. */
    private Object applyConfigGuard(String key, Object payload) {
        if (!"config".equals(key) || !(payload instanceof Map<?, ?> rawMap)) {
            return payload;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        rawMap.forEach((k, v) -> {
            if (!(k instanceof String sk)) {
                return;
            }
            if (DC_SECRET_KEYS.contains(sk)) {
                return;
            }
            safe.put(sk, v);
        });
        return safe;
    }

    /** admin 캐시 갱신 (V2 seed 또는 Sales Form Polish 슬라이스 admin endpoint 후속). */
    @CacheEvict(value = "bootstrap", allEntries = true)
    public void evictAll() {
        sheetCache.clear();
        sheetsClient.invalidateCache();
        log.info("Bootstrap cache evicted (sheet cache + spring cache)");
    }

    /** 테스트용 — sheet prefetch 결과 직접 주입 (production 호출 X). */
    void putSheetCacheForTest(String cacheKey, Object payload) {
        sheetCache.put(cacheKey, payload);
    }

    private Object parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException ex) {
            log.error("Bootstrap payload JSON parse failed: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "bootstrap cache payload 파싱 실패", ex);
        }
    }
}
