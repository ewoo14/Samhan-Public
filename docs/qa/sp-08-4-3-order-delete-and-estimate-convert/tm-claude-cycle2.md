## Claude 5-agent 사이클 2 통합 리뷰 (head `d6364d4b`)

> tech-manager agent 가 BE / FE / Designer / QA (후공정) / DevOps 5 agent 결과 종합.

### 사이클 1 결함 해소 표

| Agent | 사이클 1 결함 | 해소 | 비고 |
|---|---|---|---|
| BE | 9 | 9/9 | P1-1~4 + Codex P1 신규 + P2-6/7 + Nit-1/2 전원 |
| FE | 8 | 8/8 | variant="danger" 2건 + mock query param 2건 + Modal 본문 + route id + key 보조 |
| Designer | 6 | 6/6 | PNG 5장 한글 정상 (사용자 직접 확인), Modal/variant/successBanner/listBackLink |
| QA | 6 | 5/6 | QA-P2-01 (AlreadyConverted 첫 요청 body 단언) 사이클 1.5 선언 vs 실제 미완 |
| DevOps | 0 | — | 사이클 1 APPROVE, 신규 결함 없음 |

전체 해소율 **28/29 (96.5 %)**. 미해소 1건 QA-P2-01 은 사이클 2.5 fix 대상.

### 사이클 2 신규 발견 종합 표

| 출처 | ID | 우선순위 | 위치 | 내용 | 처리 권고 |
|---|---|---|---|---|---|
| BE | P2-1 | **Medium** | `PartnerOrder.createFromEstimate` + `testFromEstimateSuccess` L95 | 견적 변환 주문 status 가 `CONFIRMING` (advisory lock + DC/reserve/slip 진행 중) — 의미상 `DRAFT` 가 정확. outbox scheduler 가 `CONFIRMING` 필터 시 오동작 가능 | **사이클 2.5 fix** — status DRAFT + confirmedAt null + IT expect 수정 |
| BE | P2-2 | Low | `HttpHeaderConstants` + `HeaderAuthenticationFilter` L25 | `X-User-Role` 만 리터럴 문자열 잔존, 상수 누락 | 사이클 2.5 — `CALLER_ROLE_HEADER` 추가 |
| BE | P2-3 | Low | `PartnerOrderRepository.findBySourceEstimateId` | soft-delete 후 재변환 시 `ALREADY_CONVERTED` 통과 → 중복 주문 생성 가능. 정책 문서 미명시 | Javadoc 정책 명시 또는 native query 교체 (후속 슬라이스 가능) |
| FE | FE-C2-01 | 경고 | 수정 Modal `partnerCode` Input L426-430 | 거래처 코드 편집 가능 상태 — BE `PartnerOrderUpdateService` 변경 허용 여부 contract 미확인. 외부 거래처 계약 키 임의 변경 위험 | BE contract 확인 → 미허용 시 `readOnly` + payload 제외 |
| FE | FE-C2-02 | **경고 (기능)** | `SalesPartnerOrderListPage.toOrderPathId` + Detail page useParams.id | 슬립번호 `/` → `-` 치환 후 BE 역변환 없음 → 실 API 404. mock regex 가 가려둠. 사이클 1.5 route id 통일 fix 의 부작용 | **사이클 2.5 fix** — Detail page 에서 `replace(/-/g, '/')` 역변환 또는 목록 navigate 시 변환 제거 + `encodeURIComponent(o.orderNumber)` 만 사용 |
| FE | FE-C2-03 | 정보 | `updateMutation.onSuccess setQueryData(['partner-order', id], ...)` L97 | queryKey 경로 변환 문자열 — getPartnerOrder 와 일치하므로 캐시 정합. FE-C2-02 fix 시 함께 재검토 | FE-C2-02 fix 와 동반 |
| Designer | D1 | P1 | `.successBanner` color 토큰 | `.errorBanner` 는 fallback 없음 vs `.successBanner` 는 `var(--state-success, #10b981)` fallback 병기. `#10b981` 은 statusSent text `#065f46` 대비 AA 4.5:1 미달 가능 | fallback 제거 또는 `#059669` (AA 통과) 통일 |
| Designer | D2 | P1 | `04-from-estimate-already-converted.png` | 에러 코드 `PARTNER_ORDER_FROM_ESTIMATE_ALREADY_CONVERTED` 카드 본문 직접 노출. 이카운트 reference 는 한국어 사용자 메시지만 | FE spec — `errorCode` `<details>` 또는 콘솔 only |
| Designer | D3 | Nit | `.historyRow` border `var(--line-default)` vs `.estTable` `var(--c-line)` | scope 변수 출처 불명확 + 시각 무게 차이 | `--c-line` 또는 중간값 통일 검토 (후속) |
| QA | QA2-P2-01 | P2 | `testFromEstimateAlreadyConverted` L129 | 첫 요청 body 단언 (`orderNumber.exists()`) 미추가 — 사이클 1.5 선언 vs 실제 미완 | **사이클 2.5 fix** — body 단언 추가 |
| QA | QA2-P2-02 | P2 | dev-report §6 | "신규 IT 9건" → 11건, "4 PNG" → 5장 미갱신. `feedback_continuous_docs_sync` 가드 위반 | **사이클 2.5 fix** — dev-report §6 수치 + §4 D6 추가 |
| QA | QA2-Nit-01 | Nit | PNG 02 mock 목록 행 | 삭제된 `2026/05/17-1` 잔존 표시 — soft delete 후 목록 제외 정책과 시각 불일치 | mock fixture 조정 (후속) |
| QA | QA2-Nit-02 | Nit | `resolveActorName` null fallback "system" | Javadoc 명시 권장 | Javadoc 보강 (후속) |
| DevOps | D-1 | 후속 | `FixtureEstimateClient.findById` 항상 empty | `@Profile("!production")` / `@ConditionalOnMissingBean` 가드 누락 — Phase 11 cutover 전 실 client 필요 | 후속 슬라이스 백로그 (현 PR blocker 아님) |
| DevOps | D-2 | 후속 | `nextOrderNo` soft-delete row 제외 시 시퀀스 중복 가능 | DB unique 가 잡으나 500 전파 위험 | 후속 슬라이스 보완 |
| DevOps | D-3 | 후속 | `parseActorId` nil UUID fallback | audit log "시스템 자동" vs "헤더 파싱 실패" 구분 어려움 — 운영 모니터링 이슈 | 후속 슬라이스 보완 |

**합계**: P0 0 / P1·Medium·경고(기능) 3 / Low·P2·경고 5 / 정보·Nit·후속 8.

### 각 agent 종합 판정

| Agent | 판정 | 비고 |
|---|---|---|
| BE | **사이클 3 필요** | P2-1 Medium (status 의미 오류) + P2-2/3 Low |
| FE | **사이클 3 필요** | FE-C2-02 경고 (실 API 404) — Codex 평가 대기 필요 |
| Designer | **APPROVE** | D2 는 FE spec 레벨, D1·D3 후속. PNG 5장 한글 정상 확인 |
| QA | **조건부 APPROVE** | 사이클 2.5 fix 2건 (dev-report §6 + C3 body 단언) 후 최종 승인 |
| DevOps | **APPROVE** | CI 24/24, Flyway/GitGuardian/whitespace 클린. D-1~D-3 후속 백로그 |

### TM 결정

**종합 판정 — 사이클 2.5 fix 권고 (사용자 정책 N=3 사이클 2.5 후 사이클 3 0 P0/P1 도달)**

사이클 2.5 fix 후보 (Claude 5-agent 관점 우선순위):

1. **BE P2-1** (Medium / 운영 영향) — `createFromEstimate` status `CONFIRMING` → `DRAFT` + `confirmedAt = null` + `testFromEstimateSuccess` expect 수정. outbox scheduler 오동작 차단.
2. **QA2-P2-01 + QA2-P2-02** (continuous docs sync 가드 위반 + IT 검증 강화) — `testFromEstimateAlreadyConverted` 첫 요청 `orderNumber.exists()` body 단언 + dev-report §6 IT 11 / PNG 5 수치 갱신 + §4 D6 추가.
3. **BE P2-2** (Low / 상수 일관성) — `HttpHeaderConstants.CALLER_ROLE_HEADER = "X-User-Role"` 추가 + `HeaderAuthenticationFilter` 리터럴 치환.
4. **Designer D1 + D2** — `.successBanner` color 토큰 fallback 통일 (D1) + PNG 04 errorCode 노출 정책 FE spec 전달 (D2 — collapsible `<details>` 또는 콘솔 only).
5. **FE-C2-02** (경고 — 실 API 404) — Detail page `useParams.id` 에서 `replace(/-/g, '/')` 역변환 또는 목록 navigate 시 변환 제거. **Codex 사이클 2 평가가 invalid (예: 슬립번호 자체에 `/` 미포함) 로 판정 시 skip**, 그 외 fix 필수.

사이클 2.5 제외 (후속 슬라이스 백로그):

- BE P2-3 soft-delete 중복 정책 Javadoc
- FE-C2-01 partnerCode readOnly (BE contract 확인 선행 필요)
- FE-C2-03 queryKey 정보
- Designer D3 historyRow border 토큰
- QA2-Nit-01/02 mock fixture + Javadoc
- DevOps D-1/D-2/D-3 (Phase 11 cutover 전 처리)

### 메모리 가드 확인

| 가드 | 결과 |
|---|---|
| `feedback_uuid_no_user_visibility` | PASS — PNG 5장 UUID 미노출 (QA 검증) |
| `project_korean_accounting` | N/A (본 슬라이스 회계 영향 없음) |
| `feedback_korean_commits` | PASS — 사이클 1.5 commit 한국어 |
| `feedback_no_dev_director_mention` | PASS |
| `feedback_role_naming_full` | PASS — PARTNER role-guard PNG 풀네임 |
| `feedback_pr_qa_screenshots` | PASS — PNG 5장 (Designer D2 errorCode 노출 1건 fix 권고) |
| `feedback_continuous_docs_sync` | **WARN** — dev-report §6 수치 미갱신 (QA2-P2-02) |

### PM 위임 (TM 책임 외)

- Codex 사이클 2 5 agent 결과 종합 대기 — FE-C2-02 평가 + 추가 결함 cross-check
- 사이클 2.5 fix commit (Codex 종합 후 본 통합 + Codex tm 통합 비교, 우선순위 합의)
- 사이클 3 0 P0/P1 도달 시 PR 본문 갱신 + CI watch + 머지 요청

**tech-manager — 2026-05-17**
