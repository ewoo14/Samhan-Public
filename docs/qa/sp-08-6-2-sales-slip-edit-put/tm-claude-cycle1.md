## Claude 5-agent 사이클 1 통합 리뷰 (head `8de41715`)

> tech-manager 통합 — BE/FE/Designer/QA/DevOps. SP-08-6-2 매출 수정 direct PUT.
> **본 코멘트는 PR comment 등록 누락 보완** (5 agent TM 통합 게시 원칙 — agent 가 직접 PR comment 게시 X, TM 통합 1건만 게시).

### CI 상태 (head A 시점)

22 SUCCESS + 1 IN_PROGRESS + 0 FAILURE (CI head A 그린 도달).

### 결함 종합 표

| # | 출처 | 우선순위 | 위치 | 내용 |
|---|---|---|---|---|
| 1 | Designer | **BLOCKER (D-C1-1)** | `SlipDetailPage.tsx` L2182~2252 + `global.css` | 매출 수정 modal `.sales-edit-field` 미선언 — `purchase-edit-field` 그대로 사용. 의미 분리 SP-08-5-2 회고 위반 |
| 2 | Designer | **BLOCKER (D-C1-2)** | `SlipDetailPage.tsx` L2176 + `global.css` | `success-banner` 클래스 global.css 미선언 — 매출 재로드 성공 메시지 스타일 미적용. 매입 modal 도 동일 |
| 3 | QA | **BLOCKER (F-01)** | `SalesSlipUpdateIT.java` | `@MockBean UserInternalClient` + `ArologisDispatchClient` 2종 누락 — Eureka context 로드 실패 가능 |
| 4 | Designer | **MAJOR (D-C1-3)** | PNG 01 | mock 제목 "매입 전표 수정" — copy-paste 오류 (매출 슬라이스) |
| 5 | QA | **MAJOR (F-02)** | `SalesSlipUpdateIT.testSalesUpdateNonOutboundForbidden` | INBOUND 403 시 `SLIP_UPDATE_NON_SALES` 에러코드 단언 누락 |
| 6 | FE | **Medium (F-1)** | `SlipDetailPage.tsx` supervisionAddress | state/sync/body/Input 4중 누락 — 감리주소 데이터 유실 결함 |
| 7 | BE | MEDIUM (D2) | `Slip.updateSalesHeader` L740 | `withProjectInfo(null, ...)` businessNumber 인자 null 하드코딩 — 독해 시 오독 위험 |
| 8 | BE | MEDIUM (D3) | summarize 중복 + supervisionAddress audit 누락 | SalesSlipUpdateService + SlipUpdateService 5 메서드 중복. supervisionAddress audit 미포함 (감리주소 단독 변경 시 audit 미기록) |
| 9 | DevOps | MEDIUM (D-C1-2) | IT 중복 SalesSlipUpdateIT vs SlipSalesUpdateIT | 동일 endpoint 양쪽 IT 검증 — 통합/한 쪽 삭제 결정 필요 |
| 10 | Designer | MINOR (D-C1-4) | PNG 04 | "매입 전표 수정 권한이 없습니다" — 매출로 정정 |
| 11 | BE | LOW (D5) | `SalesSlipUpdateController.resolveName` | callerName 없을 때 callerId (UUID) 폴백 — UUID 비공개 정책 위반 |
| 12 | BE | LOW (D4) | `createSlip` helper | INBOUND 전표 sourceWarehouseId=null DB 제약 불명확 |
| 13 | FE | LOW (F-2) | `SALES_EDIT_ROLES` vs `canQuerySales` 중복 선언 | 단일 진실 소스 |
| 14 | FE | LOW (F-3) | salesEditLines 0 시 저장 disabled | 라인 추가 버튼 부재 — 탈출 불가 |

### 각 agent 종합 판정

| Agent | 판정 |
|---|---|
| BE | 사이클 2 필요 (HIGH D1 — MockBean 누락 / Medium D2/D3 / LOW D4/D5) |
| FE | 사이클 2 필요 (Medium F-1 supervisionAddress) |
| Designer | **CHANGES REQUESTED** (BLOCKER D-C1-1/2 + Major D-C1-3 + Minor D-C1-4) |
| QA | 사이클 2 필요 (BLOCKER F-01 MockBean + Major F-02 에러코드) |
| DevOps | 사이클 2 필요 (CRITICAL MockBean — D-C1-1 동일) |

### TM 결정 (사용자 6/7회차 정책 — 1c 일괄 fix)

**1c Claude fix 후보 (11건 일괄)**:
1. Designer BLOCKER D-C1-1: `.sales-edit-field` CSS 신규 + className 9 위치 교체
2. Designer BLOCKER D-C1-2: `.success-banner` global.css 신규 (success-200/50/700 토큰)
3. QA + DevOps BLOCKER (MockBean): `SalesSlipUpdateIT` 에 `UserInternalClient` + `ArologisDispatchClient` 추가
4. Designer Major D-C1-3 + Minor D-C1-4: PNG 01/04 "매입" → "매출" + 재생성
5. QA Major F-02: NonOutbound 에러코드 단언 추가
6. FE Medium F-1: supervisionAddress 4중 fix (state + sync + body + Input)
7. BE Low D5: actorName UUID 폴백 → "system"
8. IT 중복: SalesSlipUpdateIT 삭제 + SlipSalesUpdateIT 정본 (이관 케이스 포함)

**1c skip (후속)**:
- BE D2 (withProjectInfo null 명시적 주석)
- BE D3 (SlipSummarizer 공통 유틸 추출 — 후속 슬라이스)
- FE F-2/F-3 (LOW)
- BE D4 (LOW)

**CI green 유지 확인** + Codex 2a review 진행.

**tech-manager — 2026-05-18**
