package com.samhanair.logis.slip.publish;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Phase 6 M5 (slip-service-integration) — legacy ecount warehouseCode → 내부 warehouse UUID
 * 매핑.
 *
 * <p>설계 §3: legacy 가 사용한 warehouseCode 는 {@code "00003"} (본사), {@code "2"} (후발),
 * {@code "14"} (안성), {@code "1"} (창원) 등 짧은 문자열. SamhanLogis 내부에서는 warehouse
 * 마스터의 UUID 를 사용하므로 발행 시점에 변환이 필요하다.
 *
 * <p>단계적 접근:
 * <ol>
 *   <li>현 슬라이스 — 환경 변수 + application.yml 의 정적 매핑 ({@code app.publish.warehouse-code-map})
 *       사용. CI/Local 에서는 dev 기본값 (random UUID 4개) 적용.</li>
 *   <li>후속 슬라이스 — warehouse-service 가 추가되면 RestClient lookup 으로 교체. 본 매퍼는
 *       cache-aside 패턴으로 진화.</li>
 * </ol>
 *
 * <p>매핑 누락 → {@link BusinessException}({@link ErrorCode#INVALID_INPUT}) 으로 즉시 실패.
 * legacy 가 신규 코드를 보낸 경우 운영자가 환경 변수에 추가해야 한다.
 *
 * <p>설정 예 ({@code application.yml}):
 * <pre>
 * app:
 *   publish:
 *     warehouse-code-map:
 *       "00003": "11111111-1111-1111-1111-111111111111"  # 본사
 *       "2":     "22222222-2222-2222-2222-222222222222"  # 후발
 *       "14":    "33333333-3333-3333-3333-333333333333"  # 안성
 *       "1":     "44444444-4444-4444-4444-444444444444"  # 창원
 * </pre>
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "app.publish")
@Getter
@Setter
public class WarehouseCodeMapper {

    /** Spring 이 yaml/env 에서 주입. key 는 legacy 코드, value 는 내부 warehouse UUID. */
    private Map<String, String> warehouseCodeMap = new HashMap<>();

    @PostConstruct
    void logEffectiveMap() {
        if (warehouseCodeMap.isEmpty()) {
            log.warn("[Phase 6 M5] app.publish.warehouse-code-map 비어있음. "
                    + "from-estimate / from-partner-order 호출 시 모두 INVALID_INPUT 으로 실패. "
                    + "환경 변수 또는 application.yml 에 매핑 추가 필요.");
            return;
        }
        log.info("[Phase 6 M5] warehouse-code-map 로드: {} entries", warehouseCodeMap.size());
    }

    /**
     * legacy warehouseCode 를 내부 UUID 로 변환.
     *
     * @param warehouseCode legacy 코드 (예: "00003", "2", "14", "1")
     * @return 매핑된 warehouse UUID
     * @throws BusinessException(INVALID_INPUT) 매핑 누락 또는 입력이 비어있을 때
     */
    public UUID resolve(String warehouseCode) {
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "warehouseCode 가 비어있습니다");
        }
        String uuidStr = warehouseCodeMap.get(warehouseCode.trim());
        if (uuidStr == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "매핑되지 않은 warehouseCode: '" + warehouseCode + "'. "
                            + "운영자가 app.publish.warehouse-code-map 에 추가 필요.");
        }
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "warehouseCode '" + warehouseCode + "' 의 매핑값이 UUID 형식이 아닙니다: " + uuidStr);
        }
    }
}
