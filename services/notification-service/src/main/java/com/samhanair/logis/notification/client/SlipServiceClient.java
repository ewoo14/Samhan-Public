package com.samhanair.logis.notification.client;

import java.time.LocalDate;
import java.util.List;

/**
 * slip-service 출고전표 lookup client (PR-E1 BE-4 의존성).
 *
 * <p>본 PR-E1 BE-4 (배차안내 SMS batch 발송) 가 동시 진행 중인 slip-service 의 신규 endpoint
 * {@code GET /internal/slips/outbound?from=<date>&to=<date>} 에 의존한다. slip-service 가 endpoint
 * 를 발행하기 전까지는 본 interface 는 implementation 없이 정의만 존재 — IT 에서 {@code @MockBean}
 * 으로 격리하여 본 PR 단독 빌드 / 테스트가 가능하다 (memory feedback_it_mockbean_external_clients).
 *
 * <p>호출 측 (DispatchBatchPreviewService) 책임:
 * <ul>
 *   <li>응답 비어있을 시 빈 미리보기 반환 (오류 아님).</li>
 *   <li>각 row 의 partner_code → ChatRoomMappingRepository / BlockedPartnerLookupClient 에 이어서 라우팅.</li>
 * </ul>
 *
 * <p>Phase 11 AWS 전환 시점에 RestClient/WebClient 기반 구현체를 본 패키지에 추가하여 Spring 자동
 * 주입 — 본 interface 는 변경 없음 (계약 안정성 보장).
 */
public interface SlipServiceClient {

    /**
     * 기간(from, to inclusive) 출고전표 조회.
     *
     * <p>slip-service 측 구현 권장 사항:
     * <ul>
     *   <li>SlipType=OUTBOUND, status &ge; ACCEPTED (배차 확정 단계 이상) 필터.</li>
     *   <li>partner_code 미보유 슬립은 응답에서 제외 (호출 측 라우팅 불가능).</li>
     *   <li>OUTBOUND 호환 deliveryTag 만 (당일/야적/지방/로젠/경동 ...) — 기사 안내 대상.</li>
     * </ul>
     *
     * @param from 시작 일자 (inclusive)
     * @param to 종료 일자 (inclusive)
     * @return 매칭 슬립 N건 (없으면 빈 리스트)
     */
    List<OutboundSlipDto> getOutboundSlips(LocalDate from, LocalDate to);
}
