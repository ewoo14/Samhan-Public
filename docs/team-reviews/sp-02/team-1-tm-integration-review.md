# SP-02 TM 통합 리뷰 — 회계 마감 메뉴 gap

작성: 2026-05-16 | PM/TM: Codex

---

## 1. 5-agent 진행 중간 종합

| 역할 | 결론 |
|---|---|
| Backend | DevOps 지적으로 MANAGER route guard와 `GET /accounting/closings` 권한 충돌을 확인. 조회/list + 마감 SSE만 `ACCOUNTANT / MANAGER / MASTER`로 맞추고 실행/역마감 권한은 보존. 추가로 홈택스 batch preview `batchId=null` 회귀를 저장 반환 entity 기준으로 보정. |
| Frontend | 매출 마감 entry가 legacy `/warehouse/closing`을 가리키고 월말 마감 entry가 없는 UI gap 확인. `/sales/closing`, `/accounting/period-close`로 정렬. |
| Designer | 문서가 안내하는 업무명은 `매출 마감`, `월말 마감`으로 분리하는 것이 명확하다. 회계 그룹에서 둘 다 발견 가능해야 한다. |
| DevOps | Docker/Testcontainers `accounting-service` gate 필수. nullable JPQL 필터 500 회귀와 기존 disabled IT 5건을 모두 활성 검증. |
| QA | SP02-01~06 캡처로 route/권한/UUID 비노출 확인. `accounting-service` 전체 204 tests / skipped 0 통과를 PR gate로 기록. |

---

## 2. Cross-check

| 항목 | 판정 | 근거 |
|---|---|---|
| 메뉴 발견성 | PASS | 판매/회계 `매출 마감`, 회계 `월말 마감` entry 추가/정정. |
| API contract | PASS | `GET /accounting/closings`와 마감 SSE 조회 권한만 MANAGER read-only로 확장. DTO/스키마 변경 없음. |
| 권한 정합성 | PASS | route guard는 기존 `ACCOUNTING_ROLES`, 실행 버튼은 `canExecuteClosing` 유지. MANAGER POST 실행 403. |
| 테스트 무스킵 | PASS | Docker/Testcontainers `:services:accounting-service:test` 204 tests / 0 skipped. |
| 문서 정합성 | PASS | 월말 마감 매뉴얼의 MANAGER 조회 전용 명시 보강. |
| UUID 비공개 | PASS | 내부 row id는 action param 전용, 캡처/문서에는 노출하지 않음. |

---

## 3. 잔여 위험

- legacy `/warehouse/closing` route와 `MonthEndClosingPage`가 아직 남아 있다. 본 PR은 사이드바 정식 목적지를 바꾸는 보정이며, legacy route retire 여부는 별도 정리 PR에서 판단한다.
- `매출 마감`이 판매/회계 두 그룹에 동시에 보이는 구조는 매뉴얼과 일치하지만, 장기적으로 role별 즐겨찾기/검색이 도입되면 중복 메뉴 정리가 가능하다.
- 에이전트 감사 결과, 구매조회 검수 CTA/품목 마스터/전표 작성 route guard/창고 재고 조회 IA가 후속 P0 후보로 남았다.

---

## 4. PM 결론

SP-02는 SP-01과 같은 UI gap 유형이지만, 화면 진입성 보정을 실제 API 권한까지 닫아야 한다. 구현은 회계 마감 메뉴/조회 계약에 좁게 유지했고, PR에는 6장 캡처, static contract 테스트, Docker accounting-service 무스킵 gate를 포함한다.
