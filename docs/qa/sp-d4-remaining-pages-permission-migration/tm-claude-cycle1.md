# 🔵 Claude TM 통합 리뷰 — SP-D4 Cycle 1

**HEAD**: `6d141002`
**PR**: #244
**리뷰어**: Claude 5-agent 병렬 (BE / FE / Designer / QA / DevOps)
**CI**: ✅ 22/22 PASS (slip-it-core 2m15s / accounting+partner 진행 완료 / 전체 green)

## 종합 판정: **FIX 요청** — cycle 2 통합 fix 필요 (32 결함, P0/CRITICAL 7건)

---

## P0 / CRITICAL — 7건 (머지 차단)

### BE
1. **P0-1 ArologisAdminController 19 endpoint guard 누락**
   - 21 endpoint 중 2개에만 `checkEdit` 적용 (`parse-kakao`, `dispatches POST`)
   - 누락: `manualCreate` / `list` / `findById` / `autoMatch` / `assignDriver` / `updateStopStatus` / `softDelete` / `preClassify` / `unassigned` / `regional` / `audit-logs` / `realtime` / `edit-requests` 등 **19개**
   - Plan §7 위험 완화 "100% IT 커버" 약속 위반
2. **P0-2 ProductController 6 write endpoint guard 누락**
   - `PATCH /{id}` / `PATCH /{id}/price` / `PUT /{id}/tags` / `POST /{id}/discontinue` / `POST /{id}/reactivate` / `DELETE /{id}` — roleHeader 파라미터 자체 없음
3. **P0-3 WarehouseController 4 write endpoint guard 누락**
   - `PATCH /{id}` / `DELETE /{id}` / `POST /audit/revert/{revisionNo}` / `POST /restore`

### Designer
4. **F-D-01 `--color-warning-400` 토큰 미등록** — `tokens.css` 에 50/200/300/500/700/800 만 존재, 400 누락 → `PermissionMatrixPage` dirty 마커 색상 소실
5. **F-D-02 `--color-success-600` / `--color-danger-600` 토큰 미등록** → toast 배경 색상 소실

### QA
6. **D1 ArologisAdminPermissionIT 외부 client `@MockBean` 4종 누락**
   - 누락: `NotificationClient` / `PartnerClient` / `SlipClient` / `SlipServiceClient`
   - 기존 `ArologisDynamicPermissionIT` 는 4종 모두 격리. SP-D4 신규 IT 에서 누락 시 컨텍스트 로드 500 트랩

### DevOps
7. **DO-1 `flywayInfo` / `flywayValidate` Gradle task 실행 불가**
   - `services/auth-service/build.gradle` 에 `id 'org.flywaydb.flyway'` 플러그인 미등록
   - 전체 프로젝트 0건. `flyway-core` 는 implementation 의존성으로만 등록 (Spring Boot 내장 실행)
   - 가이드 명령 실행 시 `Task 'flywayInfo' not found` 오류

---

## P1 / HIGH — 11건 (cycle 2 fix 필수)

### FE / QA (mock.ts ↔ V10 seed 불일치)
- **F1 / D2** mock.ts MANAGER VIEW 에 `admin.users` 포함 (V10=FALSE) — 사이드바 노출 회귀
- **F2** mock.ts SALES EDIT 에 `products.list` 포함 (V10 view-only)
- **F3** mock.ts WAREHOUSE EDIT 에 `sales.vendor-order` 포함 (V10 view-only)
- **F4** mock.ts INVENTORY EDIT 에 `products.list` 포함 (V10 view-only)
- **F5** AppLayout SALES/ACCOUNTANT/DISPATCH "창고 운영" 빈 그룹 헤더 (`_showInventoryStock` 전역 true)

### BE
- **P1-1** PartnerOrderConfirmController `@PreAuthorize` SALES 누락 (Plan §2 매트릭스 위반)
- **P1-2** PartnerBlockAdminController / PartnerEditRequestController guard 미연결
- **P1-3** arologis DynamicPermissionClientImpl `@Qualifier("loadBalancedRestClientBuilder")` 미사용

### QA
- **D3** T05/T14 시나리오 URL 불일치 (`/admin/users` vs `/admin/permission-matrix`, `/inventory/audit` vs `/warehouse/audit`)

### Designer
- **F-D-03** `아로지스` → `아로로지스` ([feedback_arologis_name.md](.claude/memory/feedback_arologis_name.md) 정식 표기 `o` 2개)

### DevOps
- **DO-2** dry-run 검증 쿼리 `created_by='sp-d4-v10'` 불일치 (실제 'system')
- **DO-3** `permission_guard_denied_total` Prometheus 메트릭 미구현 (MeterRegistry/Counter 0건)

---

## P2 / MINOR — 14건 (후속 이연 가능)

- **F6** `/sales/estimates` 라우트 RoleGuard 미적용 (Plan §1 위반, `/sales/partner-orders` 와 비대칭)
- **F7** PermissionMatrixPage `PAGES_WITH_EDIT` Set 에 `inventory.audit` 누락 → MASTER 토글 불가
- **F8** AppLayout lint 경고 `DISPATCH_BOARD_SIDEBAR_ROLES` 미사용 상수
- **F-D-04** PermissionMatrixPage `fontSize: 12` 리터럴 → token 화 권장
- **D4** T06/T09 시나리오 URL (`/inventory/warehouses` vs `/warehouses`)
- **D5** PartnerAdminPermissionIT `partners.block` deny case 누락 (5 case 가이드, 4 case 구현)
- **D6** domain-integrity-check.md SQL 7번 NOT IN 절 SP-D1~D3 PageCode 미보완
- BE P2 — arologis `[SP-D3]` 로그 태그 잔류 / PageCode Javadoc 총계 41개 오기재(43) / PartnerOrderHistoryController Javadoc 보존 미명시 / WarehouseController `/search` checkView 미적용 의도 불명확
- 그 외 잔여 항목

---

## 일관성 점수

| Team | 점수 | 비고 |
|---|---|---|
| BE | 3/5 | P0 3건 — endpoint coverage 큰 누락 |
| FE | 4/5 | mock.ts ↔ V10 매트릭스 정합 회귀 |
| Designer | 3/5 | 토큰 미등록 2건 CRITICAL |
| QA | 4/5 | ArologisIT MockBean 누락 + 시나리오 URL drift |
| DevOps | 4/5 | Flyway plugin / metric 미구현 가이드 정정 필요 |

## 운영 영향

CI green / 컴파일 PASS 이지만 **사용자 노출 가드 coverage 약 30% 누락** (Arologis 19 / Product 6 / Warehouse 4 = 29 write endpoint). 운영 배포 시 RoleGuard 가 1차 차단 중이므로 운영 사고 위험은 낮음, 다만 SP-D4 슬라이스 약속 (이중 가드 100%) 미달.

## cycle 2 fix 권장 (Codex 1 commit 통합)

1. P0 7건 + P1 11건 즉시 fix
2. P2 일부 함께 해소 (F6/F7/F8/D4/D5/D6 등 비차단)
3. cycle 3 안 (양쪽 0결함) 완료 정책 준수

---

상세 5-team 리뷰 본문:
- [`docs/qa/sp-d4-remaining-pages-permission-migration/claude-be-cycle1.md`](docs/qa/sp-d4-remaining-pages-permission-migration/claude-be-cycle1.md)
- [`docs/qa/sp-d4-remaining-pages-permission-migration/claude-fe-cycle1.md`](docs/qa/sp-d4-remaining-pages-permission-migration/claude-fe-cycle1.md)
- [`docs/qa/sp-d4-remaining-pages-permission-migration/claude-designer-cycle1.md`](docs/qa/sp-d4-remaining-pages-permission-migration/claude-designer-cycle1.md)
- [`docs/qa/sp-d4-remaining-pages-permission-migration/claude-qa-cycle1.md`](docs/qa/sp-d4-remaining-pages-permission-migration/claude-qa-cycle1.md)
- [`docs/qa/sp-d4-remaining-pages-permission-migration/claude-devops-cycle1.md`](docs/qa/sp-d4-remaining-pages-permission-migration/claude-devops-cycle1.md)

**TM 결정: FIX 요청 → Codex 5-section 재검 → cycle 2 통합 fix → head B 재리뷰**

Claude TM — 2026-05-18
