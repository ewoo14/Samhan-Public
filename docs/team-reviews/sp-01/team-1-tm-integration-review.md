# SP-01 TM 통합 리뷰 — 거래처 관리 메뉴 gap

작성: 2026-05-16 | PM/TM: Codex

---

## 1. 5-agent 산출 종합

| 역할 | 결론 |
|---|---|
| Backend | `partner-service` 목록/검색/4탭 등록 권한을 `SALES / MANAGER / MASTER`로 정합화. SALES 조회/등록 IT와 UUID 비노출 assertion 추가. |
| Frontend | `/admin/partners`와 `/admin/partners/new`를 공용 RoleGuard 라우트로 분리하고, `판매 > 거래처 관리` entry 추가. |
| Designer | 화면 라벨은 `거래처 마스터`보다 `거래처 관리` 권장. 판매 업무 흐름에서 발견 가능해야 함. |
| QA | SP01-01~14 시나리오와 상세 캡처 체크리스트 작성. PM 조정으로 SALES 등록 차단 가정은 등록 허용 계약으로 정정. |
| DevOps | UI-only라면 desktop/design-system gate가 핵심이나, 본 PR은 backend 권한 계약 변경이 포함되어 `partner-service:test`까지 필수 gate로 확장. |

---

## 2. Cross-check

| 항목 | 판정 | 근거 |
|---|---|---|
| API contract | PASS | FE `PARTNER_FULL_ROLES`와 BE `@PreAuthorize`가 `SALES / MANAGER / MASTER`로 정렬. |
| UUID 비공개 | PASS | Backend IT에서 `id` 비노출 assertion 추가, FE route는 `partnerCode` 기반. |
| 메뉴 발견성 | PASS | `AppLayout` 판매 그룹에 `sidebar-sales-partners` 추가. |
| 대표실 인사 셸 회귀 | PASS | `admin-nav-partners` test id 유지, 라벨만 `거래처 관리`로 정리. |
| 문서 정합성 | PASS | 영업 매뉴얼/FAQ/QA/dev-report 갱신. |

---

## 3. 잔여 위험

- `PartnerCreatePage`의 `partnerCode` 자동 생성은 여전히 timestamp 기반 임시 구현이다. 후속으로 partner-service 서버 채번 정책을 도입하는 것이 좋다.
- 거래처 상세 편집 권한은 endpoint별로 아직 `MANAGER / MASTER` 제한이 남아 있다. 이번 PR은 신규 등록과 목록 발견성 해결이 목적이며, ACTIVE 거래처 수정 잠금/승인 UX는 Phase 12 후속 정비 범위다.
- 실제 운영 데이터 정합성은 활성 `partner_code`, `biz_no`, 기본 배송지, 주 담당자 중복 SQL로 추가 점검한다.

---

## 4. PM 결론

SP-01은 단순 메뉴 추가가 아니라 P0-6 거래처 UI의 문서-프론트-백엔드 권한 계약을 맞추는 통합 보정이다. PR 발행 전 `partner-service` 전체 테스트, desktop typecheck/lint/build, Playwright static contract, QA 캡처 raw 링크 검증을 모두 통과시킨다.
