## Codex 5-agent 사이클 1 2a 통합 리뷰 (head `de2053e7`)

> tech-manager agent 가 Codex BE / FE / Designer / QA (후공정) / DevOps 5 agent 결과 종합.
> 5회차 워크플로우 2a 단계 — Claude fix 정합 + Codex 자체 신규 발견.

### Claude fix 정합 검증 종합

| Claude fix | Codex 평가 | 사유 |
|---|---|---|
| FE D-1 CRITICAL window.open → apiClient.get blob + URL.createObjectURL | **부분 정합 — Codex 신규 CRITICAL** | apiClient blob 방향 맞으나 `noopener,noreferrer` + 반환값 null 가능 → 탭 열려도 실패 처리 위험 |
| BE P1-1 PartnerLookupClient 거래처명 동적 조회 | VALID | 거래처명 정합, fallback partnerCode |
| BE P1-2 Content-Type charset=UTF-8 헤더 검증 | VALID | header().string + TEXT_HTML 호환 |
| BE/Designer D1 statusLabel 한국어 매핑 | VALID | DRAFT/CONFIRMING/CONFIRMED/CANCELED → 초안/확인 중/확정/취소 |
| Designer D2 Pretendard Variable 우선 | PARTIAL — PNG 시각 증거 부족 | BE inline CSS 정합, QA PNG 가 System.Drawing mock 으로 실제 렌더 미검증 |
| Designer D3 거래처명 정합 | VALID | PartnerLookupClient + fallback |
| FE D-3 print.module.css 제거 | VALID | dead code 회피 |
| FE D-4 RoleGuard PARTNER 차단 | VALID | SALES_PARTNER_ORDER_ROLES 적용 |
| FE D-2 dead guard 제거 | VALID | |
| HttpHeaderConstants.PARTNER_CODE_HEADER | VALID | 통합 |
| categoryLabel "기타" + confirmedAt fallback | VALID | |
| IT orderNo 포맷 일관 | VALID | |
| dev-report §6 PASS 갱신 | VALID | |

### Codex 자체 신규 발견 (사이클 1 2a)

| # | 출처 | 우선순위 | 위치 | 내용 |
|---|---|---|---|---|
| 1 | BE/DevOps | **BLOCKER (보안)** | `PartnerOrderPrintController:34` + `PartnerOrderPrintService.effectiveRole():67` | X-User-Role header spoofing — header role 이 SecurityContext 보다 우선 → PARTNER 인증 사용자가 `X-User-Role: SALES` 보내면 본인 주문 제한 우회. **권한 우회**. `callerRole` 파라미터 제거 + `SecurityContextHolder.getContext().getAuthentication().getAuthorities()` 만 사용 + 회귀 IT (`@WithMockUser(roles="PARTNER")` + `X-User-Role: SALES` + 타 거래처 → 403) |
| 2 | FE | **CRITICAL** | `SalesPartnerOrderDetailPage.tsx:170` | `window.open(url, '_blank', 'noopener,noreferrer')` 반환값 null 가능 → 탭 열려도 FE 가 실패 처리 + Blob URL revoke + 에러 배너. 핵심 인쇄 플로우 사용자 실패처럼 보임. 빈 창 먼저 열기 패턴 또는 noopener 유지 + null 체크 제거 |
| 3 | Designer | P1 (D5) | `scripts/generate-...screenshots.ps1` | QA PNG System.Drawing 좌표 mock (Malgun Gothic) — 실제 @media print/Pretendard Variable/table CSS 시각 검증 부재. Playwright headless print preview 캡처 또는 사용자 Edge 캡처 필요 (사이클 2 사용자 caprture 의무) |
| 4 | Designer | P1 (D6) | `PartnerOrderPrintService.java:184` inline CSS | `tr { page-break-inside: avoid }` 만 — `thead { display: table-header-group }`, `.summary/.memo/.sign-grid { break-inside: avoid }` 누락. 라인 증가 시 합계/요청사항/날인란 분리 위험 |
| 5 | Designer | P2 (D7) | PNG 02/03 | A4 mock 제목 위치 겹침 — 좌표 조정 |

### 각 agent 종합 판정 (2a)

| Agent | 판정 |
|---|---|
| BE | **사이클 2 필요 (BLOCKER 보안 X-User-Role spoofing)** |
| FE | **사이클 2 필요 (CRITICAL noopener + null 체크 충돌)** |
| Designer | 사이클 2 필요 (D5/D6 P1) |
| QA | APPROVE (Claude fix 정합, 신규 0) |
| DevOps | **사이클 2 필요 (BLOCKER 보안 — BE 와 동일)** |

### TM 결정 (2c Codex fix 책임)

- **종합**: Claude fix 정합. **신규 BLOCKER 2건** (X-User-Role spoofing 보안 + noopener null 충돌 UX) **반드시 fix 후 사이클 2 진입**.
- **Codex fix 후보 (2c 단계)**:
  1. **BE/DevOps BLOCKER**: `PartnerOrderPrintController` `callerRole` 파라미터 제거 + `PartnerOrderPrintService.effectiveRole()` SecurityContextHolder 만 사용 + 회귀 IT (`@WithMockUser(roles="PARTNER")` + `X-User-Role: SALES` 헤더 위조 + 타 거래처 주문 → 403 단언). header CALLER_ROLE_HEADER 주입 제거.
  2. **FE CRITICAL**: `handlePrint` `window.open` 패턴 변경:
     ```ts
     const opened = window.open('', '_blank');
     if (!opened) { URL.revokeObjectURL(url); setPrintErrorMessage(...); return; }
     opened.opener = null;
     opened.location.href = url;
     ```
     또는 `noopener` 유지 + null 체크 제거 (탭 자체는 열린 것으로 가정).
  3. **Designer D6**: BE inline CSS + `print.module.css` 가 제거됐으니 BE inline 에 `thead { display: table-header-group }`, `.summary/.memo/.sign-grid { break-inside: avoid }` 추가.
  4. Designer D5 (P1): QA PNG 실제 렌더 — 본 사이클 sandbox EPERM 차단 → 사이클 2 사용자 캡처 또는 CI artifact 전환 (후속). 본 fix 에서는 PNG 좌표 조정 (D7) 정도만.
- **skip 사이클 2 이후**:
  - Designer D5 (실제 HTML 렌더 PNG) — Codex sandbox EPERM, CI 또는 사용자 Edge 캡처 필요 (`feedback_print_design_iteration.md`)
- **사이클 2 목표**: 양쪽 0 P0/P1 도달 (보안 BLOCKER + noopener CRITICAL 모두 해소)

**tech-manager — 2026-05-17**
