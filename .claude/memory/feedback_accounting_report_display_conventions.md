---
name: accounting-report-display-conventions
description: 회계 보고서 표시 규약 — 음수='-X' 빨강(괄호X), 계정명에 코드 prefix 금지(별도 코드열만), 0='—'(eCount 관행)
metadata:
  type: feedback
---

회계 보고서(데스크톱) 금액·계정명 표시 규약 (2026-06-23 개발책임자 라이브 화면 지적, 월별손익분석 #574):

- **음수 = `-1,234` (하이픈 prefix) + 빨강**. 괄호 `(1,234)` 금지, 삼각형 `△ 1,234` 금지. 색상(빨강)은 유지. ※ `currencyUtils.ts`의 `△` 포맷, 일부 페이지 모델의 `(${abs})` 괄호 포맷이 혼재했음 — 회계 보고서는 `-` 통일.
- **계정명에 계정코드 prefix 금지**. 시드 chart_of_accounts는 `code`/`name` 분리(깨끗, 예 code='401' name='상품매출'). FE가 `\`${code} ${name}\`` 로 prepend("401 상품매출")하면 개발책임자가 "옛 이카운트 코드 섞임"으로 오인. **계정명만 표시**하거나, 코드가 필요하면 **별도 '코드' 열**(시산표 #573 방식)로 분리. 매트릭스형(월별손익 등)은 계정명만.
- **0 = `—`** (em-dash). eCount 관행, 개발책임자 "이카운트 그대로" 확정. (합계=기간 전표활동이라 빈 기간엔 0='—', 잔액은 누적이라 값 — 정상.)
- **비용성 행 증감 색상**: 전기비교 difference는 단순 당기-전기 산술차. 비용 섹션(COST_OF_SALES/SGA/INCOME_TAX)은 비용증가가 difference +로 나오므로 증감 **색상 중립**(녹색=좋음 오해 방지).

**Why:** 개발책임자가 라이브 Docker 실QA 캡처를 육안 리뷰하며 표시 규약을 직접 교정. 화면 QA가 정적 검증이 못 보는 표시 결함을 적발하는 채널.

**How to apply:** 회계 보고서 신규 슬라이스(E 원장·F 전표현황·G 채권채무·H 입출금매칭 등) FE 구현 시 **처음부터 이 규약 적용**. 기 머지분 중 코드 prefix 잔존(슬B 현금흐름 fundsFlowComparisonPageModel 상대계정)은 후속 정렬. design-system 공용 금액 포맷터로 수렴 권장. 참조 [[per-round-live-qa]] / [[real-server-check-screenshot]] / [[no-fake-data-ever]].
