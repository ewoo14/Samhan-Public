# SP-03 5-agent + TM 통합 리뷰

작성: 2026-05-16 | 부모 PM: Codex | 슬라이스: 구매관리 입고 검수 CTA 복구 + 관리형 메뉴명 정리

## 1. 5-agent read-only 진단 요약

| 역할 | 결론 | 반영 |
|---|---|---|
| Frontend | `/purchases`가 `PurchaseQueryPage`로 통합되며 legacy `SlipListPage(INBOUND)`의 검수 CTA가 누락됐다. `SlipQueryRow.status`, mock status, Dialog refetch 필요. | 반영 |
| QA | WAREHOUSE/MANAGER/MASTER, SAVED/CONFIRMED, Dialog, 저장/완료, UUID 비노출, SALES negative 시나리오 필요. | 반영 |
| Backend | `SlipResponse`는 이미 `id/status/slipNo`를 제공한다. inventory 검수 권한은 WAREHOUSE/MANAGER/MASTER이며 INVENTORY는 제외. Gateway stripped path 404 가능성 지적. | 반영 |
| DevOps | desktop build/static Playwright는 Docker 불필요. inventory/slip IT는 Docker 필요이며 skip은 PASS로 인정하면 안 됨. PR 본문 hard gate 표 필요. | 반영 |
| Designer | 검수 컬럼은 권한자에게만 노출, SAVED/CONFIRMED은 secondary small button, 나머지는 `—`, INVENTORY는 비노출. mock 구매번호 `-IN1` 제거 필요. | 반영 |

## 2. TM 통합 판단

| 항목 | 판단 | 근거 |
|---|---|---|
| UI 누락 | 실 결함 | 매뉴얼은 구매관리 행 검수 버튼을 정식 경로로 안내하지만 `PurchaseQueryPage`에 버튼/Dialog가 없었다. |
| Backend 신규 API | 불필요 | `GET /slips/query`가 이미 `status/id/slipNo`를 반환하고, `InboundInspectionDialog` API도 존재한다. |
| Backend path fix | 필요 | Gateway `StripPrefix=2` 후 `/inventory/inbound-inspections/**`로 도착하므로 controller dual mapping이 필요하다. |
| 권한 | WAREHOUSE/MANAGER/MASTER | inventory-service `InboundInspectionController` 권한과 일치. INVENTORY는 현 계약상 미포함. |
| UUID 정책 | UI/캡처 비노출 | API 응답 내부 id는 path param/React key로만 사용하고 화면/test id는 `slipNo` 기반. |
| 업무번호 정책 | 서비스/메뉴/업무 타입별 독립 | 판매/구매 같은 `YYYY/MM/DD-1` 중복 허용. 내부 구분은 업무 타입 + UUID PK. |
| IA 명칭 | 관리형 화면은 `…관리` | 판매관리/구매관리/재고이동 관리/창고 관리/견적서 관리/주문서 관리로 정리. 주문서 승인/거래처 DC 설정은 유지. |
| 이동번호 정합성 | `T-`/`TR-` prefix 제거 + 마지막 순번 이후 채번 | 재고이동도 메뉴/업무 타입으로 구분되므로 `YYYY/MM/DD-N` 표준을 따르고, 같은 날짜의 numeric suffix 최댓값 + 1을 사용한다. |

## 3. Cross-team 호환성

| 검사 | 결과 |
|---|---|
| Frontend ↔ Backend | `SlipQueryRow.status`가 backend `SlipResponse.status`와 정렬됨. |
| Frontend ↔ Inventory API | Dialog path param으로만 `row.id`를 넘기고 화면에는 구매번호 표시. |
| Gateway ↔ Inventory service | `/api/v1/inventory/**` → `/inventory/**` 도착 경로 수신 보강. |
| Design ↔ 권한 | 메뉴와 구매관리 CTA 모두 `canInspectInbound()` helper 사용. |
| QA ↔ 문서 | 매뉴얼, QA 시나리오, 캡처 체크리스트가 같은 role/status matrix 사용. |
| IA ↔ UX | 생성·상세·수정/요청 흐름을 품은 화면은 조회 전용처럼 보이지 않도록 `…관리` 라벨 사용. |

## 4. PR 본문 첨부 기준

- QA 캡처 6장 raw 링크 인라인 첨부.
- 검증 표에는 `clients/desktop` typecheck/lint/build, SP-03 Playwright static, design-system build, inventory/slip Docker IT, UUID grep, `git diff --check`를 포함한다.
- GitHub CI green 후 PM이 재점검하고 merge한다.
