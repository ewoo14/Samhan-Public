## Claude 5-agent 사이클 2 통합 리뷰 (head `0caa16ab`)

> tech-manager agent 가 BE / FE / Designer / QA (후공정) / DevOps 5 agent 결과 종합.
> 5회차 워크플로우 사이클 2 1a 단계.

### 사이클 1 결함 해소 표

| Agent | 해소 |
|---|---|
| BE | 7/7 (P1 2 + BLOCKER 보안 + statusLabel + HttpHeaderConstants + categoryLabel + confirmedAt) |
| FE | 7/7 (D-1 CRITICAL + Codex noopener + dead guard + print.module.css + RoleGuard + AuthSnapshot + interceptor) |
| Designer | 7/7 (D1~D7 + Codex D6 pagination + D7 PNG 좌표) |
| QA | 7/7 (BE-1 partnerName + IT-1 orderNo + DOC-1 + Content-Type + IT-6 spoof + BLOCKER + CRITICAL) |
| DevOps | 1/1 (HttpHeaderConstants) |

**사이클 1 28건 전원 해소.**

### 사이클 2 신규 발견 종합 표

| # | 출처 | 우선순위 | 위치 | 내용 |
|---|---|---|---|---|
| 1 | QA | **CRITICAL** | `spec.ts:36` T2 | `expect(page).toContain("window.open(url, '_blank', 'noopener,noreferrer')")` — 구현은 noopener 제거. CI Playwright spec 미실행 (workflow project 제외 가능성) 으로 SUCCESS 처리되나 실 단언 FAIL. spec 갱신 필수 |
| 2 | QA | **CRITICAL** | `spec.ts:63/65` T5 | `expect(controller).toContain('HttpHeaderConstants.CALLER_ROLE_HEADER')` — controller 에서 제거됨. `expect(service).toContain('"PARTNER".equalsIgnoreCase')` — `"ROLE_PARTNER".equals(authority.getAuthority())` 로 변경. spec 갱신 필수 |
| 3 | QA | P2 | `docs/dev-reports/sp-08-4-4-order-print-form.md` §5 | "IT 5 case" → "IT 6 case" 갱신 누락 + 신규 `testPrintPartnerSpoofedRoleHeaderRejected` 목록 추가 |
| 4 | BE | P2 (minor) | `testPrintPartnerSpoofedRoleHeaderRejected` | 테스트 의도 모호 — 실제 거부 원인은 partnerCode 불일치, X-User-Role 위조 자체는 무관. 메서드명/주석 명확화 |
| 5 | FE | LOW | `handlePrint` L181 | `setTimeout(() => URL.revokeObjectURL, 60_000)` dangling timer — useRef cleanup 패턴 권고 |
| 6 | FE | Info | `handlePrint` L165 | X-Partner-Code 이중 주입 (axios interceptor + handlePrint 명시 headers) — PARTNER role 만 영향 |
| 7 | FE | Info | `PRINT_ROLES` / `EDIT_ROLES` / `SALES_PARTNER_ORDER_ROLES` | 3 위치 동일 배열 중복 선언 |
| 8 | Designer | Nit | `Modal` 요청사항 Input | 단행 input — Textarea 권고 |
| 9 | Designer | Nit | `.summary` ↔ `.memo` | 페이지 경계 분리 위험 (운영 5줄 이하 OK) |
| 10 | DevOps | Pending | CI 6 job IN_PROGRESS | 완료 후 확인 |

### 각 agent 종합 판정

| Agent | 판정 |
|---|---|
| BE | APPROVE (P2 minor + Nit 비블로커) |
| FE | APPROVE (LOW 1 + Info 2) |
| Designer | APPROVED (Nit 4건 후속) |
| QA | **CRITICAL 2건 (spec 불일치) — 사이클 2 fix 필수** |
| DevOps | APPROVE (pending 완료 후) |

### TM 결정

- **종합**: 사이클 1 결함 28건 전원 해소. 사이클 2 신규 CRITICAL 2건 (Playwright spec 갱신 누락) — 사이클 2 1c Claude fix 필수.
- **Claude fix 후보 (1c 단계)**:
  1. **QA C2-1**: `spec.ts` T2 L36 `'noopener,noreferrer'` → `"window.open(url, '_blank')"` (또는 `apiClient.get` + `URL.createObjectURL` 만 단언, noopener 옵션 제거 반영)
  2. **QA C2-2**: spec T5 L63 `HttpHeaderConstants.CALLER_ROLE_HEADER` 제거 → `HttpHeaderConstants.PARTNER_CODE_HEADER` 만. L65 `"PARTNER".equalsIgnoreCase` → `"ROLE_PARTNER".equals` 또는 `SecurityContextHolder` 토큰
  3. **QA C2-3**: dev-report §5 "IT 5" → "IT 6", `testPrintPartnerSpoofedRoleHeaderRejected` 목록 추가
  4. (선택) BE P2 minor: 테스트 메서드명 명확화 (`testPrintRejectsCrossPartnerEvenWithRoleHeaderSpoof`)
- **Codex 2a review 대기**: Claude fix 후 push → Codex 가 위 fix 정합 + 잔존 결함 cross-check
- **2c Codex fix**: Codex valid 미처리 항목 + 자체 신규 (있다면). 가능하면 0 P0/P1 도달.
- **사이클 3 (필요 시)**: 짧은 사이클 — 0 P0/P1 확인 → 머지. 사용자 N=3 정책 준수.

**tech-manager — 2026-05-17**
