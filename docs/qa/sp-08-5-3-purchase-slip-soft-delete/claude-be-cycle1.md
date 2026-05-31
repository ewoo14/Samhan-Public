## backend-engineer 사이클 1 리뷰 (head `7cbbd13b`)

### 결함 표

| # | 심각도 | 위치 | 내용 |
|---|---|---|---|
| BE-1 | MEDIUM | `SlipDeleteIT.java:177` (D3) | `markDeleted` 직후 동일 transaction `slipRepository.findById()` — @SQLRestriction 적용된 세션에서 1차 캐시 히트로 정상 반환 가능. `flush()` + `entityManager.clear()` 또는 native query 우회 필요 — SQLRestriction 경로 미검증 |
| BE-2 | MEDIUM | `SlipDeleteIT.java:226-248` (D8) | INSPECTING 단일 상태만 검증. DisplayName "INSPECTING 이후 단계" — COMPLETED/CONFIRMED 후속 단계 케이스 부재. 주석 정정 또는 case 추가 |
| BE-3 | LOW | `SlipDeleteService.java:69` | `actorId == null` 분기 — `parseActorId` 가 zero UUID 반환하므로 dead branch. 도메인 메서드 `deleteForPurchase(String)` null 폴백과 이중화 — 한 곳으로 정리 |
| BE-4 | LOW | `SlipDeleteController.java:71-79` | `parseActorId`/`resolveName` 유틸이 `SlipUpdateController` 중복. `BaseSlipController` 또는 `SlipHeaderUtils` 추출 권장 (이후 컨트롤러마다 동일 복사) |
| BE-5 | LOW | `SlipDeleteIT.java:54` 주석 | "8 케이스 검증" — 실 D1~D9 9건. Javadoc 불일치 |

### 긍정 사항

- **MICROS truncation verifyVersion** SP-08-5-2 일관 — PG timestamp(6) 정밀도 정합
- **도메인 메서드 순서** INBOUND guard → EDITABLE_STATUSES guard → cascade markDeleted → self markDeleted 정확. setter 미사용, @SQLRestriction 준수
- **ErrorCode HTTP 매핑**: `SLIP_DELETE_NON_INBOUND` 403 + `SLIP_DELETE_INSPECTION_COMPLETED` 422 의미 분리
- **@MockBean 외부 client 7종 격리** lenient stub `feedback_it_mockbean_external_clients` 준수
- **UUID 비공개** ApiResponse<Void> data:null, slipNo 기반 조회
- **RequestMapping 컨벤션** `/slips` SlipUpdateController/SlipController 일관
- **audit log D9** saveAndFlush 후 recordBatch("SLIP_DELETE") 1건 + auditLogRepository 직접 검증

### 종합

BE-1/2 (D3 SQLRestriction 캐시 우회, D8 후속 상태 커버리지) 사이클 2 fix 권장. 나머지 코드 품질 권고. **사이클 2 필요**.

**backend-engineer agent — 2026-05-18**
