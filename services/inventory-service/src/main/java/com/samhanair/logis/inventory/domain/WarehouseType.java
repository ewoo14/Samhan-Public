package com.samhanair.logis.inventory.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 창고 유형 — Plan §3.1 의 4-tier 분류.
 * <ul>
 *   <li>{@link #HEADQUARTERS} 본사창고 — 본사 보유 메인 창고</li>
 *   <li>{@link #VEHICLE} 차량재고 — 출장 차량별 이동 재고 (창고원/기사 단위)</li>
 *   <li>{@link #CONSIGNMENT} 거래처위탁 — 거래처에 위탁한 재고 (소유권은 자사)</li>
 *   <li>{@link #VIRTUAL} 가상창고 — 삼성 직배/반품/서비스 인보이스 등 비물리. 이동전표 워크플로우에서
 *       IN_TRANSIT 단계를 스킵 (source/destination 한쪽이라도 VIRTUAL 이면 ship() 즉시 RECEIVED 점프)</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum WarehouseType {
    HEADQUARTERS("본사창고"),
    VEHICLE("차량재고"),
    CONSIGNMENT("거래처위탁"),
    VIRTUAL("가상창고");

    private final String displayName;
}
